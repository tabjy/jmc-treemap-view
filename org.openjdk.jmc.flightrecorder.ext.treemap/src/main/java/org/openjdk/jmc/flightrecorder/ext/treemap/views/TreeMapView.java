package org.openjdk.jmc.flightrecorder.ext.treemap.views;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.memleak.ReferenceTreeModel;
import org.openjdk.jmc.flightrecorder.memleak.ReferenceTreeObject;
import org.openjdk.jmc.ui.common.util.AdapterUtil;
import org.openjdk.jmc.ui.common.util.Environment;

import com.redhat.thermostat.vm.heap.analysis.common.HistogramLoader;
import com.redhat.thermostat.vm.heap.analysis.common.HistogramRecord;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogram;
import com.redhat.thermostat.vm.heap.analysis.common.ObjectHistogramNodeDataExtractor;

import org.openjdk.jmc.ui.CoreImages;
import org.openjdk.jmc.ui.MCPathEditorInput;
import org.openjdk.jmc.ui.WorkbenchToolkit;

public class TreeMapView extends ViewPart implements ISelectionListener {
	
	private Composite container;
	private TreeMapComposite treeMap;
	private TreeMapBreadcrumb breadcrumb;

	private class LoadDumpFileAction extends Action {	
		public LoadDumpFileAction() {
			setImageDescriptor(CoreImages.DATA);
			setToolTipText("Load a heap dump file"); // TODO: i18n
		}
		
		@Override
		public void run() {
			System.out.print("opening file: ");
			String path = selectFile();
			if (path != null) {
				ObjectHistogram histogram = null;
				try {
					histogram = (new HistogramLoader()).load(path);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        TreeMap<ObjectHistogram, HistogramRecord> map = new TreeMap<>(histogram, new ObjectHistogramNodeDataExtractor());
		        TreeMapNode root = map.getRoot();
		        root.setLabel("[root]");
		        
		        treeMap.setTree(root);
		        breadcrumb.setTreeMap(treeMap);
			}
		}
		
		@SuppressWarnings("restriction")
		private String selectFile() {
			IWorkbenchWindow window = Workbench.getInstance().getActiveWorkbenchWindow();
			FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN | SWT.SINGLE);
			dialog.setFilterPath("/");
			dialog.setText("Load heap dump...");

			if (dialog.open() == null) {
				return null;
			}
			
			String fullPath = dialog.getFilterPath() + File.separator + dialog.getFileName();
			final File file = new File(fullPath);
			if (!file.exists() ) {
				throw new RuntimeException("file not found");
			}
			
			return fullPath;
		}
	}
	
	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		
		IToolBarManager toolBar = site.getActionBars().getToolBarManager();
		toolBar.add(new LoadDumpFileAction());
		
		getSite().getPage().addSelectionListener(this);
	}
	
	@Override
	public void dispose() {
		getSite().getPage().removeSelectionListener(this);
		
		super.dispose();
	}
	
	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object first = ((IStructuredSelection) selection).getFirstElement();
			IItemCollection item = AdapterUtil.getAdapter(first, IItemCollection.class);
			if (item == null) {
				return;
			}
			
			System.out.println(ReferenceTreeModel.buildReferenceTree(item).getRootObjects().get(0).toString(
					ReferenceTreeObject.FORMAT_PACKAGE | ReferenceTreeObject.FORMAT_FIELD | ReferenceTreeObject.FORMAT_STATIC_MODIFIER | ReferenceTreeObject.FORMAT_OTHER_MODIFIERS | ReferenceTreeObject.FORMAT_ARRAY_INFO 
					));
		}
	}

	@Override
	public void createPartControl(Composite parent) {		
		container = new Group(parent, SWT.NONE);
		container.setLayout(new FormLayout());

		breadcrumb = new TreeMapBreadcrumb(container, SWT.BORDER);
		FormData bcLayoutData = new FormData();
		bcLayoutData.top = new FormAttachment(0, 0);
		bcLayoutData.left = new FormAttachment(0, 0);
		bcLayoutData.right = new FormAttachment(100, 0);
		breadcrumb.setLayoutData(bcLayoutData);

		treeMap = new TreeMapComposite(container, SWT.BORDER);
		FormData tmLayoutData = new FormData();
		tmLayoutData.bottom = new FormAttachment(100);
		tmLayoutData.top = new FormAttachment(breadcrumb);
		tmLayoutData.left = new FormAttachment(0);
		tmLayoutData.right = new FormAttachment(100, 0);
		treeMap.setLayoutData(tmLayoutData);

//		TreeMapNode root = new TreeMapNode(0);
//		root.setLabel("root");
//		generateTree(root, 6, 6, false);
//
//		treeMap.setTree(root);
//		breadcrumb.setTreeMap(treeMap);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
	}

	static int generatorCounter;
	static int id = 0;

	@SuppressWarnings("Duplicates")
	public static void generateTree(TreeMapNode root, int levels, int childrenNumber, boolean random) {
		TreeMapNode node;
		if (levels == 0) {
		} else {
			Random rand = new Random();
			int children = random ? rand.nextInt(childrenNumber) + 1 : childrenNumber;
			for (int i = 0; i < children; i++) {
				int val = rand.nextInt(50);
				id++;
				node = new TreeMapNode("Node #" + id, val);
				root.addChild(node);
				generatorCounter++;
			}
			for (TreeMapNode child : root.getChildren()) {
				generateTree(child, levels - 1, childrenNumber, random);
			}
		}
	}
}
