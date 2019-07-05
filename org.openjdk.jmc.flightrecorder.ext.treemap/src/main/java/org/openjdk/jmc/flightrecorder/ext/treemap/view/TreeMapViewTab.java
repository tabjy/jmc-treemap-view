package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.management.JMException;
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
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMap;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;
import org.openjdk.jmc.flightrecorder.ext.treemap.util.Util;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import org.openjdk.jmc.flightrecorder.memleak.ReferenceTreeModel;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.ServiceNotAvailableException;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.ui.misc.DisplayToolkit;

import com.redhat.thermostat.vm.heap.analysis.common.HistogramLoader;
import com.redhat.thermostat.vm.heap.analysis.common.HistogramRecord;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogram;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogramNodeDataExtractor;

public class TreeMapViewTab extends CTabItem {
	private static final String DEFAULT_MESSAGE = "Select an Old Object Sample event to display as a treemap.";

	private CompletableFuture<TreeMapNode> treeModelCalculator;

	private Composite container;
	private StackLayout containerLayout;

	private Composite treeMapContainer;
	private TreeMapComposite treeMap;
	private TreeMapBreadcrumb breadcrumb;

	private Composite messageContainer;
	private Label message;

	public TreeMapViewTab(CTabFolder parent, int style) {
		super(parent, style);

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

		containerLayout.topControl = messageContainer;
		setControl(container);

		displayMessage(DEFAULT_MESSAGE);
	}

	@Override
	public void dispose() {
		if (treeModelCalculator != null) {
			treeModelCalculator.cancel(true);
		}

		super.dispose();
	}

	public void setModelFromFile(String filePath) {
		File file = new File(filePath);
		setText(file.getName());
		setToolTipText(file.getPath());

		treeModelCalculator = CompletableFuture.supplyAsync(() -> {
			try {
				return buildModelFromFile(filePath);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		treeModelCalculator.thenAcceptAsync(this::setModel, DisplayToolkit.inDisplayThread()).exceptionally((t) -> {
			handleException(t);
			return null;
		});
	}

	public void setModelFromJvm(DiscoveryEntry entry, String filePath) {
		File file = new File(filePath);
		setText(file.getName());
		setToolTipText(file.getPath());

		treeModelCalculator = CompletableFuture.supplyAsync(() -> {
			try {
				return buildModelFromJvm(entry, filePath);
			} catch (ServiceNotAvailableException | JMException | IOException e) {
				throw new RuntimeException(e);
			}
		});

		treeModelCalculator.thenAcceptAsync(this::setModel, DisplayToolkit.inDisplayThread()).exceptionally((t) -> {
			handleException(t);
			return null;
		});
	}

	public void setModelFromOldObjectSamples(IItemCollection events) {
		treeModelCalculator = CompletableFuture.supplyAsync((Supplier<TreeMapNode>) () -> {
			return buildModelFromOldObjectSamples(events);
		});

		treeModelCalculator.thenAcceptAsync(this::setModel, DisplayToolkit.inDisplayThread()).exceptionally((t) -> {
			handleException(t);
			return null;
		});
	}

	private TreeMapNode buildModelFromFile(String filePath) throws IOException {
		displayMessage("Loading heap dump..."); // TODO: i18n

		ObjectHistogram histogram = (new HistogramLoader()).load(filePath);

		displayMessage("Building tree model..."); // TODO: i18n
		TreeMap<ObjectHistogram, HistogramRecord> map = new TreeMap<>(histogram,
				new ObjectHistogramNodeDataExtractor());
		TreeMapNode root = map.getRoot();
		root.setLabel("[root]"); // TODO: mark not translatable

		displayMessage("Rendering tree map..."); // TODO: i18n
		return root;
	}

	private TreeMapNode buildModelFromJvm(DiscoveryEntry entry, String filePath)
			throws ServiceNotAvailableException, JMException, IOException {
		RJMXConnection rjmxConn = new RJMXConnection(entry.getConnectionDescriptor(), entry.getServerDescriptor(),
				() -> {
					displayMessage("Unable to establish a RJMX connection."); // TODO: i18n
				});
		rjmxConn.connect();

		DefaultConnectionHandle handle = new DefaultConnectionHandle(rjmxConn, null, new IConnectionListener[] {});

		MBeanServerConnection mBeanConn = handle.getServiceOrThrow(MBeanServerConnection.class);
		mBeanConn.invoke(new ObjectName("com.sun.management:type=HotSpotDiagnostic"), "dumpHeap",
				new Object[] {filePath, Boolean.TRUE}, new String[] {String.class.getName(), boolean.class.getName()});
		handle.close();
		rjmxConn.close();

		return buildModelFromFile(filePath);
	}

	private TreeMapNode buildModelFromOldObjectSamples(IItemCollection events) {
		IItem selected = events.iterator().next().iterator().next();

		if (!selected.getType().getIdentifier().equals(JdkTypeIDs.OLD_OBJECT_SAMPLE)) {
			throw new IllegalArgumentException("Selected item is not a OldObjectSample"); //$NON-NLS-1$
		}

		ReferenceTreeModel tree = ReferenceTreeModel.buildReferenceTree(events);
		return Util.buildTreefromReferenceTreeObject(tree.getRootObjects().get(0));
	}

	public void setModel(TreeMapNode root) {
		if (treeMap != null && !treeMap.isDisposed()) {
			treeMap.dispose();
		}

		treeMap = new TreeMapComposite(treeMapContainer, SWT.BORDER);
		FormData tmLayoutData = new FormData();
		tmLayoutData.bottom = new FormAttachment(100);
		tmLayoutData.top = new FormAttachment(breadcrumb);
		tmLayoutData.left = new FormAttachment(0);
		tmLayoutData.right = new FormAttachment(100, 0);
		treeMap.setLayoutData(tmLayoutData);

		treeMap.setTree(root);
		breadcrumb.setTreeMap(treeMap);

		containerLayout.topControl = treeMapContainer;
		container.layout(true, true);
	}

	public void clearModel() {
		if (treeMap != null && !treeMap.isDisposed()) {
			treeMap.dispose();
		}

		displayMessage(DEFAULT_MESSAGE);
	}

	private void handleException(Throwable e) {
		if (e instanceof CancellationException || e.getCause() instanceof CancellationException) {
			return;
		}

		FlightRecorderUI.getDefault().getLogger().log(Level.SEVERE, "Unable to load heap dump", e); //$NON-NLS-1$
		displayMessage("Unable to load tree map: " + e.getLocalizedMessage()); // TODO: i18n
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
