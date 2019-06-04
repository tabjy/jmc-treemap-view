package main.java.org.openjdk.jmc.flightrecorder.ext.treemap.views;

import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import java.util.Stack;

public class TreeMapBreadcrumb extends Canvas {

	private Stack<BreadcrumbItem> items = new Stack<>();

	private TreeMapComposite treemap;

	public TreeMapBreadcrumb(Composite parent, int style) {
		super(parent, style);
	}
}
