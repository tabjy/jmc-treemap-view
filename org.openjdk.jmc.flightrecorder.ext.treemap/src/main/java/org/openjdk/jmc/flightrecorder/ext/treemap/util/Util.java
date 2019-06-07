package org.openjdk.jmc.flightrecorder.ext.treemap.util;

import java.io.File;

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

	public static String getDefaultDumpFilePath(IServerDescriptor descriptor) {
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
