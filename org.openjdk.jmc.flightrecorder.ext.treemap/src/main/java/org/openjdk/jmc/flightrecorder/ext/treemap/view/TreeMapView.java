package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit.DiscoveryEntry;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.ext.treemap.util.Util;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.rjmx.ServiceNotAvailableException;
import org.openjdk.jmc.ui.CoreImages;
import org.openjdk.jmc.ui.common.util.AdapterUtil;

import java.io.File;
import java.io.IOException;

import javax.management.JMException;

public class TreeMapView extends ViewPart implements ISelectionListener {
	private CTabFolder tabFolder;
	private TreeMapViewTab oldObjSampleTab;

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

			TreeMapViewTab tab = new TreeMapViewTab(tabFolder, SWT.CLOSE);
			tab.setModelFromFile(path);
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

			TreeMapViewTab tab = new TreeMapViewTab(tabFolder, SWT.CLOSE);

			tab.setModelFromJvm(entry, dialog.getFilePath());
			tabFolder.setSelection(tab);
		}
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);

		IToolBarManager toolBar = site.getActionBars().getToolBarManager();
		toolBar.add(new LoadHeapDumpAction());
		toolBar.add(new RecordHeapDumpAction());

		getSite().getPage().addSelectionListener(this);
	}

	@Override
	public void dispose() {
		getSite().getPage().removeSelectionListener(this);
		super.dispose();
	}

	@Override
	public void createPartControl(Composite parent) {
		tabFolder = new CTabFolder(parent, SWT.NONE);

		oldObjSampleTab = new TreeMapViewTab(tabFolder, SWT.NONE);
		oldObjSampleTab.setText("Old Object Sample");
	}

	@Override
	public void setFocus() {
		if (tabFolder != null) {
			tabFolder.setFocus();
		}
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!setOldObjectSample(selection)) {
			clearOldObjectSample();
		}
	}

	private boolean setOldObjectSample(ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return false;
		}

		Object first = ((IStructuredSelection) selection).getFirstElement();
		IItemCollection items = AdapterUtil.getAdapter(first, IItemCollection.class);

		if (items == null) {
			return false;
		}

		if (!items.hasItems()) {
			return false;
		}

		if (!items.iterator().next().hasItems()) {
			return false;
		}

		IItem selectedItem = items.iterator().next().iterator().next();

		if (!selectedItem.getType().getIdentifier().equals(JdkTypeIDs.OLD_OBJECT_SAMPLE)) {
			return false;
		}

		oldObjSampleTab.setModelFromOldObjectSamples(items);
		tabFolder.setSelection(oldObjSampleTab);

		return true;
	}

	private void clearOldObjectSample() {
		oldObjSampleTab.clearModel();
	}
}
