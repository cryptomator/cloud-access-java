package org.cryptomator.cloudaccess.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;

public class CloudPathTest {

	@ParameterizedTest
	@MethodSource("provideTestStringsToParse")
	public void testStaticFactoryMethodTakingString(String s, List<String> elements) {
		var path = CloudPath.of(s);
		Assertions.assertIterableEquals(path.getElements(), elements);
	}

	public static List<Arguments> provideTestStringsToParse() {
		return List.of(
				Arguments.of("/hello/beatuy", List.of("hello", "beatuy")),
				Arguments.of("//hello/beatuy", List.of("hello", "beatuy")),
				Arguments.of("/hello//beatuy", List.of("hello", "beatuy")),
				Arguments.of("//", List.of()),
				Arguments.of("///", List.of())
		);
	}
}
