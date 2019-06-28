package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit.DiscoveryEntry;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMap;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;
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

public class TreeMapViewTab extends CTabItem {

	private Composite container;
	private StackLayout containerLayout;

	private static ExecutorService MODEL_EXECUTOR = Executors.newFixedThreadPool(1);
	private CompletableFuture<Void> treeModelCalculator;

	private Composite treeMapContainer;
	private TreeMapComposite treeMap;
	private TreeMapBreadcrumb breadcrumb;

	private Composite messageContainer;
	private Label message;

	public TreeMapViewTab(CTabFolder parent, String filePath) {
		super(parent, SWT.CLOSE);

		File file = new File(filePath);
		setText(file.getName());
		setToolTipText(file.getPath());

		container = new Group(parent, SWT.PUSH);
		containerLayout = new StackLayout();
		container.setLayout(containerLayout);

		messageContainer = new Group(container, SWT.NONE);
		FillLayout layout = new FillLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		messageContainer.setLayout(layout);

		message = new Label(messageContainer, SWT.LEFT | SWT.TOP | SWT.WRAP);

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

		setControl(container);
	}

	public TreeMapViewTab(CTabFolder parent, String filePath, DiscoveryEntry jvm) {
		this(parent, filePath);
	}

	@Override
	public void dispose() {
		container.dispose();
		super.dispose();
	}

	void recordAndBuildModel(DiscoveryEntry entry, String filePath) {
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

	void buildModel(String filePath) {
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
			DisplayToolkit.inDisplayThread().execute(() -> {
				try {
					setModel(root);
				} catch (Exception e) {
					handleException(e);
				}
			});

			return null;
		}, MODEL_EXECUTOR).exceptionally((t) -> {
			handleException(t);
			return null;
		});

	}

	void setModel(TreeMapNode root) {
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

	void displayMessage(String msg) {
		DisplayToolkit.inDisplayThread().execute(() -> {
			message.setText(msg);

			containerLayout.topControl = messageContainer;
			container.layout(true, true);
		});
	}
}
