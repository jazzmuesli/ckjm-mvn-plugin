package com.github.jazzmuesli.ckjm.plugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import gr.spinellis.ckjm.CkjmOutputHandler;
import gr.spinellis.ckjm.ClassMetrics;

public class CSVCkjmOutputHandler implements CkjmOutputHandler {

	private final CSVPrinter printer;
	private final Map<String, Function<ClassMetrics, Number>> metricsToCMGetters;

	public CSVCkjmOutputHandler(String fname) throws IOException {
		this.metricsToCMGetters = createMap();
		List<String> fields = new ArrayList<String>();
		fields.add("CLASS");
		fields.addAll(metricsToCMGetters.keySet());
		this.printer = new CSVPrinter(Files.newBufferedWriter(Paths.get(fname)),
				CSVFormat.DEFAULT.withHeader((String[]) fields.toArray(new String[0])).withSystemRecordSeparator().withDelimiter(';'));
	}

	static Map<String, Function<ClassMetrics, Number>> createMap() {
		List<Method> getters = Stream.of(ClassMetrics.class.getMethods())
				.filter(m -> m.getParameterCount() == 0 && m.getName().startsWith("get") && isNumeric(m))
				.collect(Collectors.toList());
		Map<String, Function<ClassMetrics, Number>> metricNamerToCMGetter = getters.stream()
				.collect(Collectors.toMap(CSVCkjmOutputHandler::getMetricNameFromMethod, CSVCkjmOutputHandler::toFunction));
		return new TreeMap<>(metricNamerToCMGetter);
	}

	static String getMetricNameFromMethod(Method k) {
		return k.getName().replaceAll("get", "").toUpperCase();
	}

	static Function<ClassMetrics, Number> toFunction(Method method) {
		Function<ClassMetrics, Number> f = c -> {
			try {
				Object ret = method.invoke(c, new Object[0]);
				return (Number) ret;
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalArgumentException(e);
			}
		};
		return f;
	}

	protected static boolean isNumeric(Method m) {
		Class<?> returnType = m.getReturnType();
		return returnType == int.class || returnType == double.class || returnType == long.class
				|| returnType == float.class;
	}

	@Override
	public void handleClass(String name, ClassMetrics c) {
		List<String> vals = new ArrayList<String>();
		vals.add(name);
		metricsToCMGetters.values().stream().map(f->f.apply(c)).forEach(v -> vals.add(String.valueOf(v)));
		try {
			this.printer.printRecord(vals.toArray(new String[0]));
			this.printer.flush();
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
