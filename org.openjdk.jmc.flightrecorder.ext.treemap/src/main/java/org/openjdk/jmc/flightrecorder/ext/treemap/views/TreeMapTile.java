package main.java.org.openjdk.jmc.flightrecorder.ext.treemap.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

public class TreeMapTile extends Composite {
	private TreeMapComposite composite;
	private TreeMapNode node;
	private Label label;

	private int colorIdx;
	private Color color;

	private List<TreeMapTile> childTiles = new LinkedList<>();

	private Cursor cursor;

	public final static int SWT_STYLE = SWT.BORDER;
	public final static Color FONT_COLOR = new Color(Display.getDefault(), 0, 0, 0);
	public final static int FONT_SIZE = 6;

	public TreeMapTile(final Composite parent) {
		super(parent, SWT_STYLE);

		super.setLayout(null);

		if (parent instanceof TreeMapComposite) {
			composite = (TreeMapComposite) parent;
		} else if (parent instanceof TreeMapTile) {
			composite = ((TreeMapTile) parent).composite;
		}

		label = new Label(this, SWT.NONE);
		FontData[] fd = label.getFont().getFontData();
		fd[0].setHeight(FONT_SIZE);
		Font font = new Font(Display.getCurrent(), fd[0]);
		label.setFont(font);

		initListeners();
	}

	@Override public void dispose() {
		if (cursor != null && !cursor.isDisposed()) {
			cursor.dispose();
		}

		super.dispose();
	}

	@Override public void setLayout(Layout layout) {
		// TODO: custom exception type
		throw new SWTException("cannot set layout to a tree map tile");
	}

	public void setNode(TreeMapNode node) {
		this.node = node;

		displayTile();
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

		setBackground(color);
	}

	public void setColor(Color color) {
		int idx = Arrays.asList(TreeMapComposite.COLORS).indexOf(color);
		if (idx == -1) {
			throw new IllegalArgumentException("Color not defined in TreeMapComponent");
		}

		setColor(idx);
	}

	public Color getColor() {
		return color;
	}

	private void initListeners() {
		Listener onMouseDown = (Event e) -> {
			switch (e.button) {
			case 1: // left button
				composite.selectTile(this);
				break;
			case 2: // middle button
				composite.zoomFull();
				break;
			case 3: // right button
				composite.zoomOut();
				break;
			}

		};
		addListener(SWT.MouseDown, onMouseDown);

		Listener onMouseDoubleClick = (Event e) -> {
			if (e.button == 1) { // left button
				composite.zoomIn(getNode());
			}
		};
		addListener(SWT.MouseDoubleClick, onMouseDoubleClick);

		addListener(SWT.MouseEnter, e -> {
			if (cursor != null && !cursor.isDisposed()) {
				cursor.dispose();
			}

			cursor = new Cursor(Display.getCurrent(), SWT.CURSOR_CROSS);

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

	private void displayTile() {
		for (TreeMapTile childTile : childTiles) {
			childTile.setVisible(false);
		}

		if (getSize().x <= 0 || getSize().y <= 0) {
			return;
		}

		// TODO: better data binding mechanism
		setToolTipText(node.getLabel());

		addLabelIfPossible();

		addChildTilesIfPossible();
	}

	// add label to tile if space permits
	private void addLabelIfPossible() {
		label.setVisible(false);

		// TODO: better data binding mechanism
		String text = node.getLabel();
		if (text == null || text.equals("")) {
			return;
		}

		Point availableSpace = getSize();

		GC gc = new GC(label);
		// TODO: better data binding mechanism
		Point textBound = gc.textExtent(node.getLabel());
		gc.dispose();

		if (textBound.x > availableSpace.x || textBound.y > availableSpace.y) {
			return;
		}

		label.setText(text);
		label.setBounds(0, 0, textBound.x, textBound.y);
		label.setForeground(FONT_COLOR);
		label.setVisible(true);
	}

	// add child tiles if space permits
	private void addChildTilesIfPossible() {
		// calculate available sub region for child tiles
		Rectangle2D.Double availableRegion = new Rectangle2D.Double(getClientArea().x, getClientArea().y,
				getClientArea().width, getClientArea().height);
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

			if (childRect.width <= TreeMapComposite.MIN_SIZE || childRect.height <= TreeMapComposite.MIN_SIZE) {
				continue;
			}

			TreeMapTile childTile;
			if (i < childTiles.size()) {
				childTile = childTiles.get(i);
			} else {
				childTile = new TreeMapTile(this);
				childTiles.add(childTile);
			}

			childTile.setVisible(true);

			childTile.setBounds((int) Math.round(squarifiedMap.get(child).x) + TreeMapComposite.X_PADDING,
					(int) Math.round(squarifiedMap.get(child).y) + TreeMapComposite.Y_PADDING,
					(int) Math.round(squarifiedMap.get(child).width),
					(int) Math.round(squarifiedMap.get(child).height));
			childTile.setColor(colorIdx + 1 % TreeMapComposite.COLORS.length);
			childTile.setNode(child);
		}
	}
}
