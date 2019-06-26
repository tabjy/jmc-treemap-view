package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.SquarifiedTreeMap;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;

import java.awt.geom.Rectangle2D;
import java.util.*;

public class TreeMapTile {
	private TreeMapComposite composite;
	private TreeMapNode node;

	private int colorIdx;
	private Color color;
	private Color borderColor;

	private Rectangle bounds;

	private Map<TreeMapNode, TreeMapTile> childTiles = new HashMap<>();

	public TreeMapTile(final TreeMapComposite composite) {
		this.composite = composite;

		// super(parent, SWT_STYLE);

		// super.setLayout(null);

//		if (parent instanceof TreeMapComposite) {
//			composite = (TreeMapComposite) parent;
//		} else if (parent instanceof TreeMapTile) {
//			composite = ((TreeMapTile) parent).composite;
//		}

//		label = new Label(this, SWT.NONE);
//		FontData[] fd = label.getFont().getFontData();
//		fd[0].setHeight(FONT_SIZE);
//		Font font = new Font(Display.getCurrent(), fd[0]);
//		label.setFont(font);

//		initListeners();
	}

//	@Override
//	public void dispose() {
//		if (cursor != null && !cursor.isDisposed()) {
//			cursor.dispose();
//		}
//
//		super.dispose();
//	}

//	@Override public void setLayout(Layout layout) {
//		// TODO: custom exception type
//		throw new SWTException("cannot set layout to a tree map tile");
//	}

	public void setNode(TreeMapNode node) {
		this.node = node;
//		displayTile();
	}

	public TreeMapNode getNode() {
		return node;
	}

	public void setColor(int idx) {
		if (idx < 0 || idx >= TreeMapComposite.COLORS.length) {
			throw new IllegalArgumentException("Color index out of bound"); // TODO: mark not translatable
		}
		colorIdx = idx;
		color = TreeMapComposite.COLORS[idx];
		borderColor = TreeMapComposite.BORDER_COLORS[idx];

//		setBackground(color);
	}

	public Color getColor() {
		return color;
	}

	public void setDarker(boolean darker) {
		if (darker) {
			RGB original = TreeMapComposite.COLORS[colorIdx].getRGB();
			color = new Color(Display.getCurrent(), //
					(int) (original.red * 0.8), (int) (original.green * 0.8), (int) (original.blue * 0.8));
		} else {
			color = TreeMapComposite.COLORS[colorIdx];
		}
	}

//	private void initListeners() {
//		Listener onMouseDown = (Event e) -> {
//			switch (e.button) {
//			case 1: // left button
//				composite.selectTile(this);
//				break;
//			case 2: // middle button
//				composite.zoomFull();
//				break;
//			case 3: // right button
//				composite.zoomOut();
//				break;
//			}
//
//		};
//		addListener(SWT.MouseDown, onMouseDown);
//
//		Listener onMouseDoubleClick = (Event e) -> {
//			if (e.button == 1) { // left button
//				composite.zoomIn(getNode());
//			}
//		};
//		addListener(SWT.MouseDoubleClick, onMouseDoubleClick);
//
//		addListener(SWT.MouseEnter, e -> {
//			if (node.isLeaf()) {
//				return;
//			}
//
//			if (cursor != null && !cursor.isDisposed()) {
//				cursor.dispose();
//			}
//
//			cursor = new Cursor(Display.getCurrent(), SWT.CURSOR_CROSS);
//
//			setCursor(cursor);
//		});
//
//		addListener(SWT.MouseExit, e -> {
//			if (cursor != null && !cursor.isDisposed()) {
//				cursor.dispose();
//			}
//
//			cursor = new Cursor(Display.getCurrent(), SWT.CURSOR_ARROW);
//
//			setCursor(cursor);
//		});
//	}

//	private void displayTile() {
//		for (TreeMapTile childTile : childTiles) {
//			childTile.setVisible(false);
//		}
//
//		if (getSize().x <= 0 || getSize().y <= 0) {
//			return;
//		}
//
//		// TODO: better data binding mechanism
//		double weight = node.getRealWeight();
//		String unit = "B";
//		if (weight > 1024) {
//			weight /= 1024;
//			unit = "KiB";
//		}
//		if (weight > 1024) {
//			weight /= 1024;
//			unit = "MiB";
//		}
//		if (weight > 1024) {
//			weight /= 1024;
//			unit = "GiB";
//		}
//		if (weight > 1024) {
//			weight /= 1024;
//			unit = "TiB";
//		}
//
//		setToolTipText(String.format("%s\n%.2f %s", node.getLabel(), weight, unit));
//
//		addLabelIfPossible();
//
//		addChildTilesIfPossible();
//	}

	// add label to tile if space permits
	private void addLabelIfPossible(GC gc) {
//		label.setVisible(false);

		// TODO: better data binding mechanism
		String text = node.getLabel();
		if (text == null || text.equals("")) {
			return;
		}

		Point availableSpace = new Point(bounds.width, bounds.height);

//		GC gc = new GC(label);
		// TODO: better data binding mechanism
		Point textBound = gc.textExtent(node.getLabel());
//		gc.dispose();

		if (textBound.x > availableSpace.x || textBound.y > availableSpace.y) {
			return;
		}

		FontData[] fd = gc.getFont().getFontData();
		fd[0].setHeight(TreeMapComposite.FONT_SIZE);
		Font font = new Font(Display.getCurrent(), fd[0]);
		gc.setFont(font);

		gc.setForeground(TreeMapComposite.FONT_COLOR);
		gc.drawText(text, bounds.x, bounds.y);

//		label.setText(text);
//		label.setBounds(0, 0, textBound.x, textBound.y);
//		label.setForeground(FONT_COLOR);
//		label.setVisible(true);
	}

	// add child tiles if space permits
	private void addChildTilesIfPossible(GC gc) {
		// calculate available sub region for child tiles
//		Rectangle2D.Double availableRegion = new Rectangle2D.Double(getClientArea().x, getClientArea().y,
//				getClientArea().width, getClientArea().height);
		Rectangle2D.Double availableRegion = new Rectangle2D.Double(0, 0, bounds.width, bounds.height);
		availableRegion.width = Math.max(0, availableRegion.width - 2 * TreeMapComposite.X_PADDING);
		availableRegion.height = Math.max(0, availableRegion.height - 2 * TreeMapComposite.Y_PADDING);

		if (availableRegion.width == 0 || availableRegion.height == 0) {
			return;
		}

		// calculate child rectangles
		// TODO: investigate why hardcoded as LinkedList
		LinkedList<TreeMapNode> elements = new LinkedList<>(Objects.requireNonNull(node.getChildren()));
		TreeMapNode.sort(elements);
		SquarifiedTreeMap algorithm = new SquarifiedTreeMap(availableRegion, elements);
		Map<TreeMapNode, Rectangle2D.Double> squarifiedMap = algorithm.squarify();

		for (int i = 0; i < elements.size(); i++) {
			TreeMapNode child = elements.get(i);
			Rectangle2D.Double childRect = squarifiedMap.get(child);

			if (childRect.width < TreeMapComposite.MIN_SIZE || childRect.height < TreeMapComposite.MIN_SIZE) {
				continue;
			}

			TreeMapTile childTile = childTiles.get(child);
			if (childTile == null) {
				childTile = new TreeMapTile(composite);
				childTiles.put(child, childTile);
			}

			Rectangle2D.Double childBounds = squarifiedMap.get(child);
			childTile.setBounds((int) childBounds.x + bounds.x + TreeMapComposite.X_PADDING,
					(int) childBounds.y + bounds.y + TreeMapComposite.Y_PADDING, (int) childBounds.width,
					(int) childBounds.height);
			childTile.setColor(colorIdx + 1 % TreeMapComposite.COLORS.length);
			childTile.setNode(child);

			childTile.draw(gc);
		}
	}

	public void setBounds(Rectangle bounds) {
		this.bounds = bounds;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.bounds = new Rectangle(x, y, width, height);
	}

	public void draw(GC gc) {
		if (composite.getSelectedNode() != null) {
			setDarker(getNode() == composite.getSelectedNode());
		}
		gc.setBackground(color);
		int[] polygon = new int[] {bounds.x, bounds.y, //
				bounds.x + bounds.width, bounds.y, //
				bounds.x + bounds.width, bounds.y + bounds.height, //
				bounds.x, bounds.y + bounds.height};

		gc.fillPolygon(polygon);

		addLabelIfPossible(gc);
		addChildTilesIfPossible(gc);

		gc.setForeground(borderColor);
		gc.drawPolygon(polygon);
	}

	public TreeMapNode findNodeAt(int x, int y) {
		if (!bounds.contains(x, y)) {
			return null;
		}

		for (TreeMapTile tile : childTiles.values()) {
			if (tile.bounds.contains(x, y)) {
				return tile.findNodeAt(x, y);
			}
		}

		return getNode();
	}
}
