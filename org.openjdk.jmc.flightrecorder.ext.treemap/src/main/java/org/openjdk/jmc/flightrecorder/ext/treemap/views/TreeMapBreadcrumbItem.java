package org.openjdk.jmc.flightrecorder.ext.treemap.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;

import java.util.Objects;

public class TreeMapBreadcrumbItem extends Canvas {

	private TreeMapNode node;

	private Point bound = new Point(0, 0);
	private Point textBound = new Point(0, 0);

	public static final int MARGIN = 4;
	public static final int ARROW_WIDTH = 4;

	public static final Color BACKGROUND_COLOR = new Color(Display.getCurrent(), 224, 224, 224);
	public static final Color FOREGROUND_COLOR = new Color(Display.getCurrent(), 64, 64, 64);
	public static final Color BORDER_COLOR = new Color(Display.getCurrent(), 212, 212, 212);

	public TreeMapBreadcrumbItem(Composite parent, int style) {
		super(parent, style);

	}

	public void setNode(TreeMapNode node) {
		this.node = Objects.requireNonNull(node);

		// calculate text bound
		GC gc = new GC(this);
		// TODO: better data binding mechanism
		textBound = gc.textExtent(node.getLabel());
		gc.dispose(); // TODO: cache GC somehow?

		bound.x = textBound.x + 2 * MARGIN + ARROW_WIDTH;
		bound.y = textBound.y + 2 * MARGIN;
	}

	public TreeMapNode getNode() {
		return node;
	}

	public int getWidth() {
		return bound.x;
	}

	public int getHeight() {
		return bound.y;
	}

	void drawAt(GC gc, int dx) {
		gc.setBackground(BACKGROUND_COLOR);
		gc.setForeground(BORDER_COLOR);
		int[] polygon = new int[] {dx, 0, //
				dx + 5, getHeight() / 2, //
				dx, getHeight(), //
				dx + getWidth(), getHeight(), //
				dx + getWidth() + 5, getHeight() / 2, //
				dx + getWidth(), 0};

		gc.fillPolygon(polygon);

		gc.setForeground(FOREGROUND_COLOR);
		gc.drawText(node.getLabel(), dx + ARROW_WIDTH + MARGIN, MARGIN, true);
	}
}
