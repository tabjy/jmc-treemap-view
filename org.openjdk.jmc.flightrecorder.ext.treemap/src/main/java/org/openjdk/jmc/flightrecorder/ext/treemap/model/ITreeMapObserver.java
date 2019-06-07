package org.openjdk.jmc.flightrecorder.ext.treemap.model;

public interface ITreeMapObserver {

	void notifySelection(TreeMapNode node);

	void notifyZoomIn(TreeMapNode node);

	void notifyZoomOut();

	void notifyZoomFull();
}
