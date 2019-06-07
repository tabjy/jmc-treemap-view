package com.redhat.thermostat.vm.heap.analysis.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.openjdk.jmc.flightrecorder.ext.treemap.model.NodeDataExtractor;

public class ObjectHistogramNodeDataExtractor implements NodeDataExtractor<ObjectHistogram, HistogramRecord> {

	static final String DELIMITER = ".";

	private static final Map<Character, String> lookupTable = new HashMap<>();

	static {
		lookupTable.put('Z', "boolean");
		lookupTable.put('B', "byte");
		lookupTable.put('C', "char");
		lookupTable.put('S', "short");
		lookupTable.put('I', "int");
		lookupTable.put('J', "long");
		lookupTable.put('F', "float");
		lookupTable.put('D', "double");
	}

	public static String toJavaType(String fieldDescriptor) {
		return toJavaType(fieldDescriptor, lookupTable);
	}

	static String toJavaType(String fieldDescriptor, Map<Character, String> lookupTable) {
		StringBuilder result = new StringBuilder();

		int arrayDimensions = 0;
		int lastLocation = 0;
		int i = -1;
		while ((i = fieldDescriptor.indexOf('[', lastLocation)) != -1) {
			arrayDimensions++;
			lastLocation = i + 1;
		}

		char indicator = fieldDescriptor.charAt(lastLocation);

		if (lookupTable.get(indicator) != null) {
			result.append(lookupTable.get(indicator));
		} else if (indicator == 'L') {
			String internalClassName = fieldDescriptor.substring(lastLocation + 1, fieldDescriptor.length() - 1);
			String commonClassName = internalClassName.replace('/', '.');
			result.append(commonClassName);
		} else {
			result.append(fieldDescriptor);
		}
		result.append(repeat("[]", arrayDimensions));

		return result.toString();
	}

	private static String repeat(String text, int times) {
		StringBuilder builder = new StringBuilder(text.length() * times);
		for (int i = 0; i < times; i++) {
			builder.append(text);
		}
		return builder.toString();
	}

	@Override
	public String[] getNodes(HistogramRecord record) {
		String className = record.getClassname();
		// if className is a primitive type it is converted with its full name
		return toJavaType(className).split(Pattern.quote(DELIMITER));
	}

	@Override
	public double getWeight(HistogramRecord record) {
		return record.getTotalSize();
	}

	@Override
	public Collection<HistogramRecord> getAsCollection(ObjectHistogram histogram) {
		return histogram.getHistogram();
	}
}
