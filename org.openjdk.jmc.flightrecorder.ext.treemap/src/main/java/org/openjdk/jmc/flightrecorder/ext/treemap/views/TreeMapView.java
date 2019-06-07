package org.openjdk.jmc.flightrecorder.ext.treemap.views;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit.DiscoveryEntry;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.rcp.application.ApplicationPlugin;
import org.openjdk.jmc.rjmx.IServerDescriptor;
import org.openjdk.jmc.ui.misc.DisplayToolkit;

import com.redhat.thermostat.vm.heap.analysis.common.HistogramLoader;
import com.redhat.thermostat.vm.heap.analysis.common.HistogramRecord;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogram;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogramNodeDataExtractor;

import org.openjdk.jmc.ui.CoreImages;
import org.openjdk.jmc.ui.common.resource.MCFile;

public class TreeMapView extends ViewPart {
	private final static String FILE_OPEN_FILTER_PATH = "file.open.filter.path"; //$NON-NLS-1$

	private static ExecutorService MODEL_EXECUTOR = Executors.newFixedThreadPool(1);
	private CompletableFuture<TreeMapNode> treeModelCalculator;

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
			dialog.setFilterPath(getDefaultFilterPath());
			dialog.setText("Load Heap Dump");

			if (dialog.open() == null) {
				return null;
			}

			return dialog.getFilterPath() + File.separator + dialog.getFileName();
		}
	}

	private class RecordHeapDumpAction extends Action {
		private RecordHeapDumpAction() {
			setImageDescriptor(CoreImages.THREAD_NEW);
			setToolTipText("Record a heap dump");
		}

		@Override
		public void run() {
			for (DiscoveryEntry entry : LocalJVMToolkit.getAttachableJVMs()) {
				System.out.println(entry.getServerDescriptor().getDisplayName());
				System.out.println(entry.getServerDescriptor().getJvmInfo().toString());
				System.out.println();
			}

			IWorkbenchWindow window = FlightRecorderUI.getDefault().getWorkbench().getActiveWorkbenchWindow();
			ElementListSelectionDialog dialog = new ElementListSelectionDialog(window.getShell(), new LabelProvider() {
				@Override
				public String getText(Object obj) {
					return ((DiscoveryEntry) obj).getServerDescriptor().getDisplayName();
				}
			}) {
				private Text fileNameText;
				
				@Override
				protected Control createDialogArea(Composite parent) {
					super.createDialogArea(parent);
					
					Composite container = new Composite(parent, SWT.NONE);
					
					GridLayout layout = new GridLayout();
					container.setLayout(layout);
					
					GridData gd1 = new GridData(SWT.FILL, SWT.FILL, true, false);
					Composite settingsContainer = createSettingsContainer(container, 0);
					settingsContainer.setLayoutData(gd1);
					
					return parent;
				}
				
				private Composite createSettingsContainer(Composite parent, int indent) {
					Composite container = new Composite(parent, SWT.NONE);
					int cols = 5;
					GridLayout layout = new GridLayout(cols, false);
					layout.horizontalSpacing = 8; // Make room for the content proposal decorator
					container.setLayout(layout);

					GridData gd1 = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Label label = new Label(parent, SWT.NONE);
					label.setText("Destination File:"); 
					label.setLayoutData(gd1);

					GridData gd2 = new GridData(SWT.FILL, SWT.CENTER, true, true);
					gd2.horizontalSpan = cols - 2;
					fileNameText = new Text(parent, SWT.READ_ONLY | SWT.BORDER);
					fileNameText.setText(getDefaultDumpFile(null));
					fileNameText.setEnabled(false);
					gd2.minimumWidth = 0;
					gd2.widthHint = 400;
					fileNameText.setLayoutData(gd2);

					GridData gd3 = new GridData(SWT.FILL, SWT.FILL, false, true);
					Button browseButton = new Button(parent, SWT.NONE);
					browseButton.setText("Browse...");
					browseButton.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							
							// Setting focus back to the button, otherwise focus
							// will just disappear!
							browseButton.setFocus();
						}
					});
					browseButton.setLayoutData(gd3);

					return container;
				}
			};
			dialog.setElements(LocalJVMToolkit.getAttachableJVMs());
			dialog.setMessage("Select a local JVM to produce a heap dump from:");
			dialog.setTitle("Select JVM");
			// user pressed cancel
			if (dialog.open() != Window.OK) {
				return;
			}

			DiscoveryEntry entry = (DiscoveryEntry) dialog.getResult()[0];
			displayMessage("Creating a heap dump from: " + entry.getServerDescriptor().getDisplayName() + "...");
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

	private void buildModel(String filePath) {
		if (treeModelCalculator != null) {
			treeModelCalculator.cancel(true);
		}

		treeModelCalculator = CompletableFuture.supplyAsync(new Supplier<TreeMapNode>() {

			@Override
			public TreeMapNode get() {
				displayMessage("Loading heap dump...");

				ObjectHistogram histogram;
				try {
					histogram = (new HistogramLoader()).load(filePath);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}

				displayMessage("Building tree model...");
				TreeMap<ObjectHistogram, HistogramRecord> map = new TreeMap<>(histogram,
						new ObjectHistogramNodeDataExtractor());
				TreeMapNode root = map.getRoot();
				root.setLabel("[root]"); // TODO: mark not translatable

				displayMessage("Rendering tree map...");
				return root;
			}

		}, MODEL_EXECUTOR);

		treeModelCalculator.thenAcceptAsync(new Consumer<TreeMapNode>() {

			@Override
			public void accept(TreeMapNode root) {
				DisplayToolkit.inDisplayThread().execute(new Runnable() {

					@Override
					public void run() {
						TreeMapView.this.setModel(root);
					}
				});

			}

		}).exceptionally(new Function<Throwable, Void>() {

			@Override
			public Void apply(Throwable t) {
				return TreeMapView.this.handleException(t);
			}

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

	private Void handleException(Throwable e) {
		System.out.println("hannde exception: " + e.getMessage());
		FlightRecorderUI.getDefault().getLogger().log(Level.SEVERE, "Unable to load heap dump", e); //$NON-NLS-1$
		displayMessage("Unable to load heap dump:" + "\n\t" + e.getLocalizedMessage()); // TODO: i18n
		return null;
	}

	private void displayMessage(String msg) {
		DisplayToolkit.inDisplayThread().execute(() -> {
			message.setText(msg);

			containerLayout.topControl = messageContainer;
			container.layout(true, true);
		});
	}

	private static String getDefaultFilterPath() {
		String result = getIfExists(ApplicationPlugin.getDefault().getDialogSettings().get(FILE_OPEN_FILTER_PATH));
		if (result == null) {
			result = getIfExists(System.getProperty("user.home"));
		}
		if (result == null) {
			result = "./"; //$NON-NLS-1$
		}

		return result;
	}
	
	private static String getDefaultDumpFile(IServerDescriptor descriptor) {
		if (descriptor == null) {
			return "";
		}
		return getDefaultFilterPath() + File.separator + "java_pid" + descriptor.getJvmInfo().getPid() + ".hprof";
	}

	private static String getIfExists(String path) {
		if (path == null) {
			return null;
		}

		return (new File(path)).exists() ? path : null;
	}
}
