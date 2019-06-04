package main.java.org.openjdk.jmc.flightrecorder.ext.treemap.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Stack;

public class TreeMapBreadcrumb extends Canvas implements ITreeMapObserver {

	private Stack<TreeMapBreadcrumbItem> items = new Stack<>();

	private TreeMapComposite treeMap;

	private Cursor cursor;

	public TreeMapBreadcrumb(Composite parent, int style) {
		super(parent, style);

		initListeners();
	}

	public void setTreeMap(TreeMapComposite treeMap) {
		setTreeMap(treeMap, treeMap.getTree());
	}

	public void setTreeMap(TreeMapComposite treeMap, TreeMapNode start) {
		if (this.treeMap != null && !this.treeMap.isDisposed()) {
			this.treeMap.unregister(this);
		}

		this.treeMap = checkTreeMap(treeMap);
		this.treeMap.register(this);

		items.clear();
		LinkedList<TreeMapNode> nodes = start.getAncestors();

		while (!nodes.isEmpty()) {
			TreeMapBreadcrumbItem item = new TreeMapBreadcrumbItem(this, SWT.NONE);
			item.setNode(nodes.removeLast());
			items.push(item);
		}
	}

	@Override public Point computeSize(int wHint, int hHint, boolean changed) {
		int width = 0;
		int height = 0;
		for (TreeMapBreadcrumbItem item : items) {
			width += item.getWidth();
			height = Math.max(height, item.getHeight());
		}
		return new Point(Math.max(width, wHint), Math.max(height, hHint));
	}

	private void initListeners() {
		addPaintListener(this::paintItems);

		addListener(SWT.MouseUp, e -> {
			TreeMapBreadcrumbItem target = null;

			int dx = 0;
			for (TreeMapBreadcrumbItem item : items) {
				dx += item.getWidth();

				if (dx >= e.x) {
					target = item;
					break;
				}
			}

			if (target != null) {
				checkTreeMap(treeMap).zoomIn(target.getNode());
			}
		});

		addListener(SWT.MouseEnter, e -> {
			if (cursor != null && !cursor.isDisposed()) {
				cursor.dispose();
			}

			cursor = new Cursor(Display.getCurrent(), SWT.CURSOR_HAND);

			setCursor(cursor);
		});

		addListener(SWT.MouseExit, e -> {
			if (cursor != null && !cursor.isDisposed()) {
				cursor.dispose();
			}

			cursor = new Cursor(Display.getCurrent(), SWT.CURSOR_ARROW);

			setCursor(cursor);
		});
	}

	private void paintItems(PaintEvent e) {
		int dx = 0;
		for (TreeMapBreadcrumbItem item : items) {
			item.drawAt(e.gc, dx);
			dx += item.getWidth();
		}
	}

	private TreeMapComposite checkTreeMap(TreeMapComposite treeMap) {
		Objects.requireNonNull(treeMap);
		if (treeMap.isDisposed()) {
			throw new IllegalArgumentException("TreeMap is already disposed"); // TODO: mark not translatable
		}

		return treeMap;
	}

	@Override public void dispose() {
		if (cursor != null && !cursor.isDisposed()) {
			cursor.dispose();
		}

		super.dispose();
	}

	@Override public void notifySelection(TreeMapNode node) {
		// intentionally empty
	}

	@Override public void notifyZoomFull() {
		for (Control child : getChildren()) {
			child.dispose();
		}

		TreeMapBreadcrumbItem item = new TreeMapBreadcrumbItem(this, SWT.NONE);
		item.setNode(checkTreeMap(treeMap).getTree());

		items.clear();
		items.push(item);
		redraw();
	}

	@Override public void notifyZoomIn(TreeMapNode node) {
		setTreeMap(treeMap, node);
		redraw();
	}

	@Override public void notifyZoomOut() {
		items.pop().dispose();
		redraw();
	}
}
