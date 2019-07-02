package org.openjdk.jmc.flightrecorder.ext.treemap.util;

import java.io.File;

import org.openjdk.jmc.common.item.IAccessorKey;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.ext.treemap.model.TreeMapNode;
import org.openjdk.jmc.flightrecorder.memleak.ReferenceTreeObject;
import org.openjdk.jmc.rcp.application.ApplicationPlugin;
import org.openjdk.jmc.rjmx.IServerDescriptor;

public class Util {
	private final static String FILE_OPEN_FILTER_PATH = "file.open.filter.path"; //$NON-NLS-1$

	public static String getDefaultFilterPath() {
		String result = getIfExists(ApplicationPlugin.getDefault().getDialogSettings().get(FILE_OPEN_FILTER_PATH));
		if (result == null) {
			result = getIfExists(System.getProperty("user.home"));
		}
		if (result == null) {
			result = "./"; //$NON-NLS-1$
		}

		return result;
	}

	public static String getDefaultFileName(IServerDescriptor descriptor) {
		return "java_pid" + descriptor.getJvmInfo().getPid() + ".hprof";
	}

	public static String getDefaultDumpFilePath(IServerDescriptor descriptor) {
		if (descriptor == null) {
			return "";
		}
		return getDefaultFilterPath() + File.separator + getDefaultFileName(descriptor);
	}

	private static String getIfExists(String path) {
		if (path == null) {
			return null;
		}

		return (new File(path)).exists() ? path : null;
	}

	public static TreeMapNode buildTreefromReferenceTreeObject(ReferenceTreeObject root) {
		if (root.getChildren().size() < 1) {
			return new TreeMapNode(root.toString(ReferenceTreeObject.FORMAT_FIELD),
					(double) computeLeafSize(root.getItems().iterator().next()));
		}

		long sum = 0;
		TreeMapNode node = new TreeMapNode(root.toString(ReferenceTreeObject.FORMAT_FIELD), 0);
		for (ReferenceTreeObject child : root.getChildren()) {
			TreeMapNode childNode = buildTreefromReferenceTreeObject(child);
			node.addChild(childNode);
			sum += childNode.getRealWeight();
		}

		node.setRealWeight((double) sum);

		return node;
	}

	private static long computeLeafSize(IItem item) {
		IAccessorKey<?> key = null;
		for (IAccessorKey<?> accessorKey : item.getType().getAccessorKeys().keySet()) {
			if (accessorKey.getIdentifier().equals("lastKnownHeapUsage")) {
				key = accessorKey;
				break;
			}
		}

		@SuppressWarnings("unchecked")
		IMemberAccessor<IQuantity, IItem> accessor = (IMemberAccessor<IQuantity, IItem>) item.getType()
				.getAccessor(key);
		long size = ((IQuantity) accessor.getMember(item)).longValue();

		return size;
	}
}
