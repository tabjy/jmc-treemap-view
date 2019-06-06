package org.openjdk.jmc.flightrecorder.ext.treemap.views;

import java.util.Collection;

public interface NodeDataExtractor<S, T> {

	/**
	 * This method extracts and returns the nodes from the key of the {@link T} element parameter.
	 *
	 * @param element a unit, holding some weight and labelled by some key (e.g. a pathname) that
	 *           expresses a delimited set of (hierarchical) nodes
	 * @return a String array, with an individual node at each index
	 */
	String[] getNodes(T element);

	/**
	 * This method returns the weight of the {@link T} element parameter.
	 */
	double getWeight(T element);

	/**
	 * This method extracts and returns the set of {@link T} elements which are aggregated in the
	 * {@link S} data parameter.
	 */
	Collection<T> getAsCollection(S data);
}
