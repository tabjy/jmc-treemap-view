package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;

import java.util.Objects;

public class TreeMapBreadcrumbItem {
	private Composite composite;
	private TreeMapNode node;

	private Point bound = new Point(0, 0);
	private Point textBound = new Point(0, 0);

	public static final int PADDING = 4;
	public static final int MARGIN_LEFT = 1;
	public static final int ARROW_WIDTH = 4;

	public static final Color BACKGROUND_COLOR = new Color(Display.getCurrent(), 255, 255, 255);
	public static final Color FOREGROUND_COLOR = new Color(Display.getCurrent(), 64, 64, 64);
	public static final Color BORDER_COLOR = new Color(Display.getCurrent(), 212, 212, 212);

	public TreeMapBreadcrumbItem(final Composite parent) {
		composite = parent;
	}

	public void setNode(TreeMapNode node) {
		this.node = Objects.requireNonNull(node);

		// calculate text bound
		GC gc = new GC(composite);
		// TODO: better data binding mechanism
		textBound = gc.textExtent(node.getLabel());
		gc.dispose(); // TODO: cache GC somehow?

		bound.x = textBound.x + 2 * PADDING + ARROW_WIDTH;
		bound.y = textBound.y + 2 * PADDING;
	}

	public TreeMapNode getNode() {
		return node;
	}

	public int getWidth() {
		return bound.x + MARGIN_LEFT;
	}

	public int getHeight() {
		return bound.y;
	}

	void drawAt(GC gc, int dx) {
		gc.setBackground(BACKGROUND_COLOR);
		gc.setForeground(BORDER_COLOR);
		int[] polygon = new int[] {dx, 0, //
				dx + 5, bound.y / 2, //
				dx, bound.y, //
				dx + bound.x, bound.y, //
				dx + bound.x + 5, bound.y / 2, //
				dx + bound.x, 0};

		gc.fillPolygon(polygon);
		gc.drawPolygon(polygon);

		gc.setForeground(FOREGROUND_COLOR);
		gc.drawText(node.getLabel(), dx + ARROW_WIDTH + PADDING, PADDING, true);
	}
}
