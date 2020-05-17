package com.github.jazzmuesli.ckjm.plugin;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

import gr.spinellis.ckjm.ClassMetrics;

public class CSVCkjmOutputHandlerTest {

	@Test
	public void test() throws Exception {
		Map<String, Function<ClassMetrics, Number>> metricsToGettersMap = CSVCkjmOutputHandler.createMap();
		ClassMetrics c = new ClassMetrics();
		c.setAmc(3.1);
		c.addLoc(314);
		Map<String, Object> metrics = metricsToGettersMap.entrySet().stream().collect(Collectors.toMap(x -> x.getKey(),
				v-> v.getValue().apply(c)));
		assertEquals(314, metrics.get("LOC"));
		assertEquals(3.1, metrics.get("AMC"));
	}

}
