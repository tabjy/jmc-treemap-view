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
	private static ExecutorService MODEL_EXECUTOR = Executors.newFixedThreadPool(1);
	private CompletableFuture<Void> treeModelCalculator;

	private Composite container;
	private StackLayout containerLayout;

	private Composite treeMapContainer;
	private TreeMapComposite treeMap;
	private TreeMapBreadcrumb breadcrumb;

	private Composite messageContainer;
	private Label message;

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

			buildModel(path);
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
			// user pressed cancel
			
			DiscoveryEntry entry = null;
			
			boolean done = false;
			while (!done) {
				if (dialog.open() != Window.OK) {
					return;
				}

				entry = (DiscoveryEntry) dialog.getResult()[0];
				
				File file = new File(dialog.getFilePath());
				if (!file.exists()) {
					break;
				} else if (MessageDialog.openConfirm(window.getShell(), "Destination file exists", "Destination file exists. Are you sure to over overwrite?")) {
					file.delete();
					break;
				}
			}
			
			displayMessage("Saving heap dump of " + entry.getServerDescriptor().getDisplayName() + " to "
					+ dialog.getFilePath() + "..."); // TODO: i18n
			
			recordAndBuildModel(entry, dialog.getFilePath());
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
		container = new Group(parent, SWT.NONE);
		containerLayout = new StackLayout();
		container.setLayout(containerLayout);

		messageContainer = new Group(container, SWT.NONE);
		FillLayout layout = new FillLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		messageContainer.setLayout(layout);

		message = new Label(messageContainer, SWT.LEFT | SWT.TOP | SWT.WRAP);
		message.setText("No heap dump available. Load or record a heap dump."); // TODO: i18n

		treeMapContainer = new Group(container, SWT.NONE);
		treeMapContainer.setLayout(new FormLayout());

		breadcrumb = new TreeMapBreadcrumb(treeMapContainer, SWT.BORDER);
		FormData bcLayoutData = new FormData();
		bcLayoutData.top = new FormAttachment(0, 0);
		bcLayoutData.left = new FormAttachment(0, 0);
		bcLayoutData.right = new FormAttachment(100, 0);
		breadcrumb.setLayoutData(bcLayoutData);

		treeMap = new TreeMapComposite(treeMapContainer, SWT.BORDER);
		FormData tmLayoutData = new FormData();
		tmLayoutData.bottom = new FormAttachment(100);
		tmLayoutData.top = new FormAttachment(breadcrumb);
		tmLayoutData.left = new FormAttachment(0);
		tmLayoutData.right = new FormAttachment(100, 0);
		treeMap.setLayoutData(tmLayoutData);

		containerLayout.topControl = messageContainer;
	}

	private void recordAndBuildModel(DiscoveryEntry entry, String filePath) {
		if (treeModelCalculator != null) {
			treeModelCalculator.cancel(true);
		}

		// FIXME: This part is really hacky and fragile. Do it properly if possible.
		treeModelCalculator = CompletableFuture.supplyAsync((Supplier<Void>) () -> {
			RJMXConnection rjmxConn = new RJMXConnection(entry.getConnectionDescriptor(), entry.getServerDescriptor(),
					() -> {
						displayMessage("Unable to establish a RJMX connection."); // TODO: i18n
					});

			try {
				rjmxConn.connect();
			} catch (ConnectionException e) {
				rjmxConn.close();
				throw new UncheckedIOException(e);
			}
			DefaultConnectionHandle handle = new DefaultConnectionHandle(rjmxConn, null, new IConnectionListener[] {});

			try {
				MBeanServerConnection mBeanConn = handle.getServiceOrThrow(MBeanServerConnection.class);
				mBeanConn.invoke(new ObjectName("com.sun.management:type=HotSpotDiagnostic"), "dumpHeap",
						new Object[] {filePath, Boolean.TRUE},
						new String[] {String.class.getName(), boolean.class.getName()});
			} catch (ConnectionException e) {
				throw new UncheckedIOException(e);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				try {
					handle.close();
				} catch (IOException e) {
					// intentionally empty
				}
				rjmxConn.close();
			}

			buildModel(filePath);

			return null;
		}).exceptionally((Throwable t) -> {
			handleException(t);
			return null;
		});

	}

	private void buildModel(String filePath) {
		if (treeModelCalculator != null) {
			treeModelCalculator.cancel(true);
		}

		treeModelCalculator = CompletableFuture.supplyAsync((Supplier<Void>) () -> {
			displayMessage("Loading heap dump..."); // TODO: i18n

			ObjectHistogram histogram;
			try {
				histogram = (new HistogramLoader()).load(filePath);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			displayMessage("Building tree model..."); // TODO: i18n
			TreeMap<ObjectHistogram, HistogramRecord> map = new TreeMap<>(histogram,
					new ObjectHistogramNodeDataExtractor());
			TreeMapNode root = map.getRoot();
			root.setLabel("[root]"); // TODO: mark not translatable

			displayMessage("Rendering tree map..."); // TODO: i18n
			DisplayToolkit.inDisplayThread().execute(() -> setModel(root));

			return null;
		}, MODEL_EXECUTOR).exceptionally((t) -> {
			handleException(t);
			return null;
		});

	}

	@Override
	public void setFocus() {
		if (containerLayout != null && containerLayout.topControl != null) {
			containerLayout.topControl.setFocus();
		}
	}

	private void setModel(TreeMapNode root) {
		treeMap.setTree(root);
		breadcrumb.setTreeMap(treeMap);

		containerLayout.topControl = treeMapContainer;
		container.layout(true, true);
	}

	private void handleException(Throwable e) {
		if (e instanceof CancellationException || e.getCause() instanceof CancellationException) {
			return;
		}

		FlightRecorderUI.getDefault().getLogger().log(Level.SEVERE, "Unable to load heap dump", e); //$NON-NLS-1$
		displayMessage("Unable to load heap dump:" + "\n\t" + e.getLocalizedMessage()); // TODO: i18n
		return;
	}

	private void displayMessage(String msg) {
		DisplayToolkit.inDisplayThread().execute(() -> {
			message.setText(msg);

			containerLayout.topControl = messageContainer;
			container.layout(true, true);
		});
	}

}
