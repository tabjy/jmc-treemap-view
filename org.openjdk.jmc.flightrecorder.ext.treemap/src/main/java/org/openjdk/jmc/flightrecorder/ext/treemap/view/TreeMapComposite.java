package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.*;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.ITreeMapObserver;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

public class TreeMapComposite extends Composite {
	private TreeMapNode tree;

	private TreeMapTile rootTile;
	TreeMapTile focusedTile;

	static final int X_PADDING = 10;
	static final int Y_PADDING = 10;
	static final int MIN_SIZE = 1;

	private Point lastDim;
	private long lastCall = 0;
	private final int MIN_DRAGGING_TIME = 60; // in ms

	private Stack<TreeMapNode> zoomStack = new Stack<>();

	static public final Color[] COLORS = {new Color(Display.getDefault(), 250, 206, 210), // red
			new Color(Display.getCurrent(), 185, 214, 255), // red
			new Color(Display.getCurrent(), 229, 229, 229), // blue
			new Color(Display.getCurrent(), 255, 231, 199), // grey
			new Color(Display.getCurrent(), 171, 235, 238), // aqua
			new Color(Display.getCurrent(), 228, 209, 252), // purple
			new Color(Display.getCurrent(), 255, 255, 255), // white
			new Color(Display.getCurrent(), 205, 249, 212), // green
	};

	private Set<ITreeMapObserver> observers = new HashSet<>();

	public TreeMapComposite(Composite parent, int style) {
		super(parent, style);

		parent.layout(true); // force to update layout so we're able to get client area
		super.setLayout(null); // use absolute layout

		lastDim = getSize();
		zoomStack.push(tree);

		initListeners();
	}

	@Override public void setLayout(Layout layout) {
		// TODO: custom exception type
		throw new SWTException("cannot set layout to a tree map composite");
	}

	public void setTree(TreeMapNode root) {
		tree = root;
		zoomStack.clear();
		zoomStack.push(tree);

		displayTree();
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

			displayTree();
		};
		addListener(SWT.Resize, onResize);

		// TODO: add keyboard event listener
	}

	private void displayTree() {
//		for (Control child : getChildren()) {
//			child.dispose();
//		}

		if (rootTile == null) {
			rootTile = new TreeMapTile(this);
		}
//		else if (rootTile.isDisposed()) {
//			rootTile = new TreeMapTile(this);
//		}

		rootTile.setBounds(0, 0, getClientArea().width, getClientArea().height);
		rootTile.setColor(0);
		rootTile.setNode(zoomStack.peek());
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

		displayTree();

		notifyZoomInToObservers(node);
	}

	public void zoomOut() {
		if (zoomStack.size() > 1) {
			zoomStack.pop();
			displayTree();

			notifyZoomOutToObservers();
		}
	}

	public void zoomFull() {
		zoomStack.clear();
		zoomStack.push(tree);
		displayTree();

		notifyZoomFullToObservers();
	}

	public void selectTile(TreeMapTile target) {
		RGB color = target.getColor().getRGB();
		Color darker = new Color(Display.getCurrent(), //
				(int) (color.red * 0.8), (int) (color.green * 0.8), (int) (color.blue * 0.8));


		if (focusedTile != null && !focusedTile.isDisposed()) {
			focusedTile.setBackground(focusedTile.getColor());
		}

		focusedTile = target;
		focusedTile.setBackground(darker);

		notifySelectionToObservers(target.getNode());
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
