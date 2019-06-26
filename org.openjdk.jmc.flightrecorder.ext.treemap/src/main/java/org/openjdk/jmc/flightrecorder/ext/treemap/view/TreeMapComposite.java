package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.ITreeMapObserver;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

public class TreeMapComposite extends Canvas {
	private TreeMapNode tree;

	private TreeMapTile rootTile;
	private TreeMapNode selectedNode;

	static final int X_PADDING = 10;
	static final int Y_PADDING = 10;
	static final int MIN_SIZE = 1;

	private Point lastDim;
	private long lastCall = 0;
	private final int MIN_DRAGGING_TIME = 60; // in ms

	private Stack<TreeMapNode> zoomStack = new Stack<>();

	private TreeMapToolTip toolTip;
	private Cursor cursor;

	public static final Color[] COLORS = {new Color(Display.getDefault(), 250, 206, 210), // red
			new Color(Display.getCurrent(), 185, 214, 255), // blue
			new Color(Display.getCurrent(), 229, 229, 229), // grey
			new Color(Display.getCurrent(), 255, 231, 199), // orange
			new Color(Display.getCurrent(), 171, 235, 238), // aqua
			new Color(Display.getCurrent(), 228, 209, 252), // purple
			new Color(Display.getCurrent(), 255, 255, 255), // white
			new Color(Display.getCurrent(), 205, 249, 212), // green
	};

	public static final Color[] BORDER_COLORS = {new Color(Display.getDefault(), 235, 194, 198), // red
			new Color(Display.getCurrent(), 168, 194, 231), // blue
			new Color(Display.getCurrent(), 214, 214, 214), // grey
			new Color(Display.getCurrent(), 227, 188, 169), // orange
			new Color(Display.getCurrent(), 148, 205, 222), // aqua
			new Color(Display.getCurrent(), 209, 192, 231), // purple
			new Color(Display.getCurrent(), 238, 238, 238), // white
			new Color(Display.getCurrent(), 190, 231, 197), // green
	};

	public final static int FONT_SIZE = 6;

	public final static Color FONT_COLOR = new Color(Display.getDefault(), 64, 64, 64);

	private Set<ITreeMapObserver> observers = new HashSet<>();

	public TreeMapComposite(Composite parent, int style) {
		super(parent, style);

		parent.layout(true); // force to update layout so we're able to get client area
		super.setLayout(null); // use absolute layout

		lastDim = getSize();
		zoomStack.push(tree);

		toolTip = new TreeMapToolTip(this);

		initListeners();
	}

	@Override
	public void setLayout(Layout layout) {
		// TODO: custom exception type
		throw new SWTException("cannot set layout to a tree map composite");
	}

	public void setTree(TreeMapNode root) {
		tree = root;
		zoomStack.clear();
		zoomStack.push(tree);

		drawRootTile();
	}

	public TreeMapNode getTree() {
		return tree;
	}

	private void initListeners() {
		Listener onResize = (Event e) -> {
			if (tree == null) {
				return;
			}

			if (System.currentTimeMillis() - lastCall < MIN_DRAGGING_TIME) {
				return;
			}

			if (lastDim.equals(getSize())) {
				return;
			}

			lastDim = getSize();

			drawRootTile();
		};
		addListener(SWT.Resize, onResize);

		addPaintListener((e) -> drawRootTile(e.gc));

		Listener onMouseDown = (Event e) -> {
			switch (e.button) {
			case 1: // left button
				TreeMapNode target = findNodeAt(e.x, e.y);
				if (target == null) {
					return;
				}
				selectNode(target);
				break;
			case 2: // middle button
				zoomFull();
				break;
			case 3: // right button
				zoomOut();
				break;
			}

		};
		addListener(SWT.MouseDown, onMouseDown);

		Listener onMouseDoubleClick = (Event e) -> {
			if (e.button != 1) { // left button
				return;
			}

			TreeMapNode target = findNodeAt(e.x, e.y);
			if (target == null) {
				return;
			}
			zoomIn(target);
		};
		addListener(SWT.MouseDoubleClick, onMouseDoubleClick);

		addListener(SWT.MouseMove, e -> {
			TreeMapNode target = findNodeAt(e.x, e.y);
			if (target == null) {
				return;
			}

			if (cursor != null && !cursor.isDisposed()) {
				cursor.dispose();
			}

			cursor = target.isLeaf() ? new Cursor(Display.getCurrent(), SWT.CURSOR_ARROW) :
					new Cursor(Display.getCurrent(), SWT.CURSOR_CROSS);
			setCursor(cursor);

			// TODO: better data binding mechanism
			double weight = target.getRealWeight();
			String unit = "B";
			if (weight > 1024) {
				weight /= 1024;
				unit = "KiB";
			}
			if (weight > 1024) {
				weight /= 1024;
				unit = "MiB";
			}
			if (weight > 1024) {
				weight /= 1024;
				unit = "GiB";
			}
			if (weight > 1024) {
				weight /= 1024;
				unit = "TiB";
			}

			toolTip.setText(String.format("%s\n%.2f %s", target.getPath("."), weight, unit));
		});
		// TODO: add keyboard event listener
	}

	private TreeMapNode findNodeAt(int x, int y) {
		if (rootTile == null) {
			return null;
		}

		return rootTile.findNodeAt(x, y);
	}

	private void drawRootTile() {
		GC gc = new GC(this);
		drawRootTile(gc);
		gc.dispose();
	}

	private void drawRootTile(GC gc) {
		if (rootTile == null) {
			rootTile = new TreeMapTile(this);
		}

		rootTile.setBounds(0, 0, getClientArea().width, getClientArea().height);
		rootTile.setColorIdx(0);
		rootTile.setNode(zoomStack.peek());

		gc.setForeground(FONT_COLOR);
		rootTile.draw(gc);
	}

	public void zoomIn(TreeMapNode node) {
		if (node.isLeaf()) {
			return;
		}

		zoomStack.clear();
		LinkedList<TreeMapNode> ancestors = node.getAncestors();
		while (!ancestors.isEmpty()) {
			zoomStack.push(ancestors.removeLast());
		}

		drawRootTile();
		redraw();

		notifyZoomInToObservers(node);
	}

	public void zoomOut() {
		if (zoomStack.size() > 1) {
			zoomStack.pop();
			drawRootTile();
			redraw();

			notifyZoomOutToObservers();
		}
	}

	public void zoomFull() {
		zoomStack.clear();
		zoomStack.push(tree);
		drawRootTile();
		redraw();

		notifyZoomFullToObservers();
	}

	public TreeMapNode getSelectedNode() {
		return selectedNode;
	}

	public void selectNode(TreeMapNode target) {
		selectedNode = target;
		redraw();

		notifySelectionToObservers(selectedNode);
	}

	public void register(ITreeMapObserver observer) {
		observers.add(observer);
	}

	public void unregister(ITreeMapObserver observer) {
		observers.remove(observer);
	}

	private void notifySelectionToObservers(TreeMapNode node) {
		for (ITreeMapObserver observer : observers) {
			observer.notifySelection(node);
		}
	}

	private void notifyZoomInToObservers(TreeMapNode node) {
		for (ITreeMapObserver observer : observers) {
			observer.notifyZoomIn(node);
		}
	}

	private void notifyZoomOutToObservers() {
		for (ITreeMapObserver observer : observers) {
			observer.notifyZoomOut();
		}
	}

	private void notifyZoomFullToObservers() {
		for (ITreeMapObserver observer : observers) {
			observer.notifyZoomFull();
		}
	}
}
