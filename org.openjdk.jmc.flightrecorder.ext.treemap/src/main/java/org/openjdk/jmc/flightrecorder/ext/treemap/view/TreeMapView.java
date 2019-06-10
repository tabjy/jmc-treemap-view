package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit.DiscoveryEntry;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMap;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;
import org.openjdk.jmc.flightrecorder.ext.treemap.util.Util;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.rjmx.ConnectionException;
import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.ui.misc.DisplayToolkit;

import com.redhat.thermostat.vm.heap.analysis.common.HistogramLoader;
import com.redhat.thermostat.vm.heap.analysis.common.HistogramRecord;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogram;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogramNodeDataExtractor;
import org.openjdk.jmc.ui.CoreImages;

public class TreeMapView extends ViewPart {
	private CTabFolder tabFolder;

	private class LoadHeapDumpAction extends Action {
		private LoadHeapDumpAction() {
			setImageDescriptor(CoreImages.FOLDER);
			setToolTipText("Load a heap dump file"); // TODO: i18n
		}

		@Override
		public void run() {
			String path = selectFile();
			if (path == null) {
				return;
			}
			
			TreeMapViewTab tab = new TreeMapViewTab(tabFolder, path);
			tab.buildModel(path);
			tabFolder.setSelection(tab);
		}

		private String selectFile() {
			IWorkbenchWindow window = FlightRecorderUI.getDefault().getWorkbench().getActiveWorkbenchWindow();
			FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN | SWT.SINGLE);
			dialog.setFilterExtensions(new String[] {"*.hprof", "*.*"});
			dialog.setFilterPath(Util.getDefaultFilterPath());
			dialog.setText("Load Heap Dump"); // TODO: i18n

			String path = dialog.open();

			if (path == null) {
				return null;
			}

			return path;
		}
	}

	private class RecordHeapDumpAction extends Action {
		private RecordHeapDumpAction() {
			// TODO: need a better icon
			setImageDescriptor(CoreImages.THREAD_NEW);
			setToolTipText("Record a heap dump"); // TODO: i18n
		}

		@Override
		public void run() {
			IWorkbenchWindow window = FlightRecorderUI.getDefault().getWorkbench().getActiveWorkbenchWindow();
			HeapDumpRecordingDialog dialog = new HeapDumpRecordingDialog(window.getShell());
			dialog.setElements(LocalJVMToolkit.getAttachableJVMs());
			dialog.setMessage("Select a local JVM to produce a heap dump from:"); // TODO: i18n
			dialog.setTitle("Select JVM"); // TODO: i18n

			DiscoveryEntry entry = null;

			boolean done = false;
			while (!done) {
				if (dialog.open() != Window.OK) {
					// user pressed cancel
					return;
				}

				entry = (DiscoveryEntry) dialog.getResult()[0];

				File file = new File(dialog.getFilePath());
				if (!file.exists()) {
					break;
				} else if (MessageDialog.openConfirm(window.getShell(), "Destination file exists",
						"Destination file exists. Are you sure to over overwrite?")) {
					file.delete();
					break;
				}
			}

			TreeMapViewTab tab = new TreeMapViewTab(tabFolder, dialog.getFilePath());
			
			tab.displayMessage("Saving heap dump of " + entry.getServerDescriptor().getDisplayName() + " to "
					+ dialog.getFilePath() + "..."); // TODO: i18n

			tab.recordAndBuildModel(entry, dialog.getFilePath());
			tabFolder.setSelection(tab);
		}
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);

		IToolBarManager toolBar = site.getActionBars().getToolBarManager();
		toolBar.add(new LoadHeapDumpAction());
		toolBar.add(new RecordHeapDumpAction());
	}

	@Override
	public void createPartControl(Composite parent) {
		tabFolder = new CTabFolder(parent, SWT.NONE);
	}

	@Override
	public void setFocus() {
		if (tabFolder != null) {
			tabFolder.setFocus();
		}
	}
}
