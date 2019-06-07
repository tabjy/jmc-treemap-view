package org.openjdk.jmc.flightrecorder.ext.treemap.model;

import java.util.List;

public class TreeMap<S, T> {

	private final TreeMapNode root;

	public TreeMap(S data, NodeDataExtractor<S, T> extractor) {
		root = new TreeMapNode("", 0);
		buildTree(data, root, extractor);
		// calculates weights for inner nodes
		fillWeights(root);
		// collapse nodes with only one child
		packTree(root);
	}

	public TreeMapNode getRoot() {
		return root;
	}

	private static <S, T> void buildTree(S data, TreeMapNode root,
			NodeDataExtractor<S, T> extractor) {

		for (T element: extractor.getAsCollection(data)) {
			TreeMapNode lastProcessed = root;
			for (String node : extractor.getNodes(element)) {
				TreeMapNode child = searchNode(lastProcessed, node);

				if (child == null) {
					child = new TreeMapNode(node, 0);
					lastProcessed.addChild(child);
				}

				lastProcessed = child;
			}
			lastProcessed.setRealWeight(extractor.getWeight(element));
		}
	}

	/**
	 * This method calculates the real weights using a bottom-up traversal.  The weight of a
	 * parent node is the sum of its children's weights.
	 *
	 * Package-private for testing purposes.
	 *
	 * @param node the root of the subtree
	 * @return the real weight of the root
	 */
	static double fillWeights(TreeMapNode node) {
		if (node.getChildren().size() == 0) {
			return node.getRealWeight();
		}

		double sum = 0;
		for (TreeMapNode child : node.getChildren()) {
			sum += fillWeights(child);
		}
		node.setRealWeight(sum);
		return node.getRealWeight();
	}

	/**
	 * This method allows the collapse of a series of nodes, each with at most one child, into a
	 * single node placed at the root of the series.
	 *
	 * Package-private for testing purposes.
	 *
	 * @param node the root of the subtree
	 */
	static void packTree(TreeMapNode node) {
		List<TreeMapNode> children = node.getChildren();
		if (children.size() == 1) {
			TreeMapNode child = children.get(0);
			node.setLabel(node.getLabel() + "." + child.getLabel());
			node.setChildren(child.getChildren());
			packTree(node);
		} else {
			for (TreeMapNode child : children) {
				packTree(child);
			}
		}
	}

	public static TreeMapNode searchNode(TreeMapNode startingPoint, String nodeId) {
		List<TreeMapNode> children = startingPoint.getChildren();
		for (TreeMapNode node : children) {
			if (node.getLabel().equals(nodeId)) {
				return node;
			}
		}
		return null;
	}
}
