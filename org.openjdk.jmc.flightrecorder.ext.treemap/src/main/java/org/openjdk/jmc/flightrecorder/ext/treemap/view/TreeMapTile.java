package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.swt.graphics.*;
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

	private Font font;

	private List<TreeMapTile> childTiles = new ArrayList<>();

	public TreeMapTile(final TreeMapComposite composite) {
		this.composite = composite;

		GC gc = new GC(composite);
		FontData[] fd = gc.getFont().getFontData();
		fd[0].setHeight(TreeMapComposite.FONT_SIZE);
		font = new Font(Display.getCurrent(), fd[0]);
	}


	public void setNode(TreeMapNode node) {
		this.node = node;
	}

	public TreeMapNode getNode() {
		return node;
	}

	public void setColorIdx(int idx) {
		if (idx < 0 || idx >= TreeMapComposite.COLORS.length) {
			throw new IllegalArgumentException("Color index out of bound"); // TODO: mark not translatable
		}
		colorIdx = idx;
		color = TreeMapComposite.COLORS[idx];
		borderColor = TreeMapComposite.BORDER_COLORS[idx];
	}

	public int getColorIdx() {
		return colorIdx;
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

		gc.setFont(font);

		gc.setForeground(TreeMapComposite.FONT_COLOR);
		gc.drawText(text, bounds.x, bounds.y);
	}

	// add child tiles if space permits
	private void addChildTilesIfPossible(GC gc) {
		// calculate available sub region for child tiles
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

		childTiles.clear();
		for (int i = 0; i < elements.size(); i++) {
			TreeMapNode child = elements.get(i);
			Rectangle2D.Double childRect = squarifiedMap.get(child);

			if (childRect.width < TreeMapComposite.MIN_SIZE || childRect.height < TreeMapComposite.MIN_SIZE) {
				continue;
			}

			TreeMapTile childTile = new TreeMapTile(composite);

			Rectangle2D.Double childBounds = squarifiedMap.get(child);
			childTile.setBounds((int) childBounds.x + bounds.x + TreeMapComposite.X_PADDING,
					(int) childBounds.y + bounds.y + TreeMapComposite.Y_PADDING, (int) childBounds.width,
					(int) childBounds.height);
			// childTile.setColorIdx((colorIdx + 1) % TreeMapComposite.COLORS.length);
			childTile.setColorIdx((colorIdx + 1) % TreeMapComposite.COLORS.length);
			childTile.setNode(child);

			childTile.draw(gc);

			childTiles.add(childTile);
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

		for (TreeMapTile tile : childTiles) {
			if (tile.bounds.contains(x, y)) {
				return tile.findNodeAt(x, y);
			}
		}

		return getNode();
	}
}
