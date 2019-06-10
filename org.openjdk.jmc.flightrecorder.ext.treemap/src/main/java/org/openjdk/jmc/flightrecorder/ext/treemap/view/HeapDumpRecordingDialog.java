package org.openjdk.jmc.flightrecorder.ext.treemap.view;

import java.io.File;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.FilteredList;
import org.openjdk.jmc.browser.attach.LocalJVMToolkit.DiscoveryEntry;
import org.openjdk.jmc.flightrecorder.ext.treemap.util.Util;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;

public class HeapDumpRecordingDialog extends ElementListSelectionDialog {
	private String filePath;
	private Text fileNameText;
	private FilteredList list;

	public HeapDumpRecordingDialog(Shell parent) {
		super(parent, new LabelProvider() {
			@Override
			public String getText(Object obj) {
				return ((DiscoveryEntry) obj).getServerDescriptor().getDisplayName();
			}
		});
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		super.createDialogArea(parent);

		// FIXME: it's hacky to get the list from ElementListSelectionDialog directly
		for (Control control : ((Composite) parent.getChildren()[0]).getChildren()) {
			if (control instanceof FilteredList) {
				list = (FilteredList) control;
			}
		}

		Composite container = new Composite(parent, SWT.NONE);

		GridLayout layout = new GridLayout();
		container.setLayout(layout);

		GridData gd1 = new GridData(SWT.FILL, SWT.FILL, true, false);
		Composite settingsContainer = createSettingsContainer(container);
		settingsContainer.setLayoutData(gd1);

		list.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				// TODO Auto-generated method stub
				DiscoveryEntry entry = (DiscoveryEntry) list.getSelection()[0];
				String path = Util.getDefaultDumpFilePath(entry.getServerDescriptor());
				fileNameText.setText(path);
				filePath = path;
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// intentionally empty
			}

		});

		return parent;
	}

	private Composite createSettingsContainer(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		int cols = 5;
		GridLayout layout = new GridLayout(cols, false);
		layout.horizontalSpacing = 8; // Make room for the content proposal decorator
		container.setLayout(layout);

		GridData gd1 = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		Label label = new Label(container, SWT.NONE);
		label.setText("Destination File:");
		label.setLayoutData(gd1);

		GridData gd2 = new GridData(SWT.FILL, SWT.CENTER, true, true);
		gd2.horizontalSpan = cols - 2;
		fileNameText = new Text(container, SWT.READ_ONLY | SWT.BORDER);
		fileNameText.setText(Util.getDefaultDumpFilePath(null));
		fileNameText.setEnabled(false);
		gd2.minimumWidth = 0;
		gd2.widthHint = 400;
		fileNameText.setLayoutData(gd2);

		GridData gd3 = new GridData(SWT.FILL, SWT.FILL, false, true);
		Button browseButton = new Button(container, SWT.NONE);
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IWorkbenchWindow window = FlightRecorderUI.getDefault().getWorkbench().getActiveWorkbenchWindow();
				FileDialog dialog = new FileDialog(window.getShell(), SWT.SAVE | SWT.SINGLE);
				dialog.setFilterExtensions(new String[] {"*.hprof"});
				dialog.setFilterPath(Util.getDefaultFilterPath());
				dialog.setFileName("");
				dialog.setText("Save Heap Dump");

				String path = dialog.open();

				// Setting focus back to the button, otherwise focus will just disappear!
				browseButton.setFocus();

				if (path == null) {
					return;
				}

				fileNameText.setText(path);
				filePath = path;
			}
		});
		browseButton.setLayoutData(gd3);

		return container;
	}

	public String getFilePath() {
		if (filePath == "") {
			return null;
		}

		return filePath;
	}
}
