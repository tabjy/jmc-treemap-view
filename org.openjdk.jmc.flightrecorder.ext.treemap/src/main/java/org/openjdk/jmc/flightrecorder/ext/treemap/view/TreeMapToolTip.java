package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;

public class TreeMapToolTip extends ToolTip {
	private String text = "";

	private Color FOREGROUND;
	private Color BACKGROUND;

	public TreeMapToolTip(Control composite) {
		super(composite);

		FOREGROUND = composite.getForeground();
		BACKGROUND = composite.getBackground();
	}

	@Override
	protected Composite createToolTipContentArea(Event event, Composite parent) {
		Composite ret = new Composite(parent, SWT.NONE);

		RowLayout rowLayout = new RowLayout();
		rowLayout.marginLeft = 5;
		rowLayout.marginTop = 5;
		rowLayout.marginRight = 5;
		rowLayout.marginBottom = 5;

		ret.setLayout(rowLayout);
		ret.setBackground(BACKGROUND);

		Label label = new Label(ret, SWT.NONE);
		label.setText(text);
		label.setForeground(FOREGROUND);

		return ret;
	}

	public void setText(String text) {
		this.text = text;
	}
}
