package main.java.org.openjdk.jmc.flightrecorder.ext.treemap.views;

public interface ITreeMapObserver {

	void notifySelection(TreeMapNode node);

	void notifyZoomIn(TreeMapNode node);

	void notifyZoomOut();

	void notifyZoomFull();
}
