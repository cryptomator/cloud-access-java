package org.cryptomator.cloudaccess.api;

import com.google.common.collect.Lists;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class CloudPathTest {

	@ParameterizedTest
	@MethodSource("provideTestStringsToParse")
	public void testStaticFactoryMethod(String s, List<String> elements, boolean expectedAbsolute) {
		var path = CloudPath.of(s);
		Assertions.assertIterableEquals(path.getElements(), elements);
		Assertions.assertEquals(expectedAbsolute, path.isAbsolute());
	}

	public static List<Arguments> provideTestStringsToParse() {
		return List.of(
				Arguments.of("/hello/beatuy", List.of("hello", "beatuy"), true),
				Arguments.of("hello/beatuy", List.of("hello", "beatuy"), false),
				Arguments.of("//hello/beatuy", List.of("hello", "beatuy"), true),
				Arguments.of("/hello//beatuy", List.of("hello", "beatuy"), true),
				Arguments.of("//", List.of(), true),
				Arguments.of("", List.of(), false),
				Arguments.of("///", List.of(), true)
		);
	}

	private CloudPath rootPath;
	private CloudPath emptyPath;

	@BeforeEach
	public void setup() {
		rootPath = CloudPath.of("/");
		emptyPath = CloudPath.of("");
	}

	@Test
	public void testIsAbsolute() {
		CloudPath p1 = path("/foo/bar");
		CloudPath p2 = path("foo/bar");
		Assertions.assertTrue(p1.isAbsolute());
		Assertions.assertFalse(p2.isAbsolute());
	}

	@Test
	public void testStartsWith() {
		CloudPath p1 = path("/foo/bar");
		CloudPath p2 = path("/foo");
		CloudPath p3 = path("foo/bar");
		CloudPath p4 = path("foo");

		Assertions.assertTrue(p1.startsWith(p1));
		Assertions.assertTrue(p1.startsWith(p2));
		Assertions.assertTrue(p1.startsWith("/foo"));
		Assertions.assertFalse(p1.startsWith("/fo"));
		Assertions.assertFalse(p2.startsWith(p1));
		Assertions.assertFalse(p1.startsWith(p3));
		Assertions.assertFalse(p1.startsWith(p4));
		Assertions.assertTrue(p3.startsWith("foo"));
		Assertions.assertTrue(p3.startsWith(p4));
		Assertions.assertFalse(p4.startsWith(p3));
	}

	@Test
	public void testEndsWith() {
		CloudPath p1 = path("/foo/bar");
		CloudPath p2 = path("bar");
		Assertions.assertTrue(p1.endsWith(p1));
		Assertions.assertTrue(p1.endsWith(p2));
		Assertions.assertTrue(p1.endsWith("bar"));
		Assertions.assertTrue(p1.endsWith("foo/bar"));
		Assertions.assertTrue(p1.endsWith("/foo/bar"));
		Assertions.assertFalse(p1.endsWith("ba"));
		Assertions.assertFalse(p1.endsWith("/foo/bar/baz"));
	}

	@Test
	public void testGetParent() {
		CloudPath p1 = path("/foo");
		CloudPath p2 = path("/foo/bar");
		CloudPath p3 = path("foo");
		CloudPath p4 = path("/");

		Assertions.assertEquals(p1, p2.getParent());
		Assertions.assertEquals(rootPath, p1.getParent());
		Assertions.assertNull(emptyPath.getParent());
		Assertions.assertNull(p3.getParent());
		Assertions.assertNull(p4.getParent());
	}

	@Test
	public void testToString() {
		CloudPath p1 = path("/foo/bar");
		CloudPath p2 = path("foo/bar");
		Assertions.assertEquals("/foo/bar", p1.toString());
		Assertions.assertEquals("foo/bar", p2.toString());
	}

	@Test
	public void testNormalize() {
		CloudPath p = path("/../../foo/bar/.///../baz").normalize();
		Assertions.assertEquals("/../../foo/baz", p.toString());
	}

	@Test
	public void testEquality() {
		CloudPath p1 = path("/foo");
		Assertions.assertNotEquals(p1, null);
		Assertions.assertNotEquals(p1, "string");

		CloudPath p2 = path("/foo");
		Assertions.assertEquals(p1.hashCode(), p2.hashCode());
		Assertions.assertEquals(p1, p2);

		CloudPath p3 = path("foo");
		Assertions.assertNotEquals(p1, p3);

		CloudPath p4 = p3.resolve("bar");
		CloudPath p5 = path("foo/bar");
		Assertions.assertEquals(p4.hashCode(), p5.hashCode());
		Assertions.assertEquals(p4, p5);

		CloudPath p6 = p1.resolve("bar");
		CloudPath p7 = p1.resolveSibling("foo/bar");
		Assertions.assertEquals(p6.hashCode(), p7.hashCode());
		Assertions.assertEquals(p6, p7);
	}

	@Test
	public void testIterator() {
		CloudPath p = path("/foo/bar/baz");
		Assertions.assertArrayEquals(Arrays.asList("foo", "bar", "baz").toArray(), Lists.newArrayList(p.iterator()).stream().map(CloudPath::toString).toArray());
	}

	@Test
	public void testGetFileName() {
		CloudPath p = path("/foo/bar/baz");
		CloudPath name = p.getFileName();
		Assertions.assertEquals(path("baz"), name);
		Assertions.assertNull(emptyPath.getFileName());
	}

	@Test
	public void testGetRootForAbsolutePath() {
		CloudPath path = CloudPath.of("/ab/so/lute");

		Assertions.assertEquals(rootPath, path.getRoot());
	}

	@Test
	public void testGetRootForNonAbsolutePath() {
		CloudPath path = CloudPath.of("ab/so/lute");

		Assertions.assertNull(path.getRoot());
	}

	@Test
	public void testToAbsolutePathReturnsThisIfAlreadyAbsolute() {
		CloudPath inTest = CloudPath.of("/ab/so/lute");

		Assertions.assertSame(inTest, inTest.toAbsolutePath());
	}

	@Test
	public void testToAbsolutePathReturnsAbsolutePathIfNotAlreadyAbsolute() {
		CloudPath inTest = CloudPath.of("ab/so/lute");
		CloudPath absolutePath = CloudPath.of("/ab/so/lute");

		Assertions.assertEquals(absolutePath, inTest.toAbsolutePath());
	}

	@Nested
	public class ResolveTest {

		@Test
		public void testResolve() {
			CloudPath p1 = path("/foo");
			CloudPath p2 = p1.resolve("bar");
			Assertions.assertEquals(path("/foo/bar"), p2);

			CloudPath p3 = path("foo");
			CloudPath p4 = p3.resolve("bar");
			Assertions.assertEquals(path("foo/bar"), p4);

			CloudPath p5 = path("/abs/path");
			CloudPath p6 = p4.resolve(p5);
			Assertions.assertEquals(p5, p6);
		}

		@ParameterizedTest(name = "test all cases where resolveSibling should return the other path")
		@CsvSource({"a,b", "/a/b,/b", "/a,/b"})
		public void testResolveSiblingReturnsOther(String p, String o) {
			CloudPath path = CloudPath.of(p);
			CloudPath other = CloudPath.of(o);

			Assertions.assertEquals(other, path.resolveSibling(other));
		}

		@Test
		public void testResolveSiblingDoesNotReturnOtherWhenOtherIsNotAbsoluteAndPathHasParent() {
			CloudPath pathWithParent = CloudPath.of("/a/b");
			CloudPath other = CloudPath.of("c");
			CloudPath expected = CloudPath.of("/a/c");

			Assertions.assertEquals(expected, pathWithParent.resolveSibling(other));
		}

	}

	@Nested
	public class EqualsTest {

		@ParameterizedTest(name = "Test different path combos are not equal ({argumentsWithNames})")
		@CsvSource({"/b,b", "/a,/b", "a,b", "a/b,b"})
		public void testEquals(String p1, String p2) {
			CloudPath a = CloudPath.of("p1");
			CloudPath b = CloudPath.of("p2");

			Assertions.assertNotEquals(a, b);
			Assertions.assertNotEquals(b, a);
		}

		@Test
		public void testEqualPathsAreEqual() {
			CloudPath a = CloudPath.of("b");
			CloudPath b = CloudPath.of("b");

			Assertions.assertEquals(a, b);
			Assertions.assertEquals(b, a);
		}

	}

	@Nested
	public class CompareToTest {

		@ParameterizedTest(name = "test compareTo returns correct sign for not equal paths")
		@CsvSource({"/a,a", "/a,/b", "/a,/a/b"})
		public void testNotEqualCases(String smaller, String bigger) {
			CloudPath p1 = CloudPath.of(smaller);
			CloudPath p2 = CloudPath.of(bigger);

			MatcherAssert.assertThat(p1, is(lessThan(p2)));
			MatcherAssert.assertThat(p2, is(greaterThan(p1)));
		}

		@Test
		public void testEqualPathsAreEqualAccordingToCompareTo() {
			CloudPath a = CloudPath.of("/a/b");
			CloudPath b = CloudPath.of("/a/b");

			MatcherAssert.assertThat(a, is(comparesEqualTo(b)));
		}

	}

	@Nested
	public class RelativizeTest {

		@Test
		public void testRelativizeWithIncompatiblePaths1() {
			CloudPath relPath = path("a");
			CloudPath absPath = path("/a");

			Assertions.assertThrows(IllegalArgumentException.class, () -> {
				relPath.relativize(absPath);
			});
		}

		@Test
		public void testRelativizeWithIncompatiblePaths2() {
			CloudPath relPath = path("a");
			CloudPath absPath = path("/a");

			Assertions.assertThrows(IllegalArgumentException.class, () -> {
				absPath.relativize(relPath);
			});
		}


		@Test
		public void testRelativizeWithEqualPath() {
			CloudPath p1 = path("a/b");
			CloudPath p2 = path("a").resolve("b");

			CloudPath relativized = p1.relativize(p2);
			Assertions.assertEquals(emptyPath, relativized);
		}

		@Test
		public void testRelativizeWithUnrelatedPath() {
			CloudPath p1 = path("a/b");
			CloudPath p2 = path("c/d");
			// a/b .resolve( ../../c/d ) = c/d
			// thus: a/b .relativize ( c/d ) = ../../c/d

			CloudPath relativized = p1.relativize(p2);
			Assertions.assertEquals(path("../../c/d"), relativized);
		}

		@Test
		public void testRelativizeWithRelativeRelatedPath() {
			CloudPath p1 = path("a/b");
			CloudPath p2 = path("a/././c");
			CloudPath p3 = path("a/b/c");

			CloudPath relativized12 = p1.relativize(p2);
			Assertions.assertEquals(path("../c"), relativized12);

			CloudPath relativized13 = p1.relativize(p3);
			Assertions.assertEquals(path("c"), relativized13);

			CloudPath relativized32 = p3.relativize(p2);
			Assertions.assertEquals(path("../../c"), relativized32);
		}

		@Test
		public void testRelativizeWithAbsoluteRelatedPath() {
			CloudPath p1 = path("/a/b");
			CloudPath p2 = path("/a/././c");
			CloudPath p3 = path("/a/b/c");

			CloudPath relativized12 = p1.relativize(p2);
			Assertions.assertEquals(path("../c"), relativized12);

			CloudPath relativized13 = p1.relativize(p3);
			Assertions.assertEquals(path("c"), relativized13);

			CloudPath relativized32 = p3.relativize(p2);
			Assertions.assertEquals(path("../../c"), relativized32);
		}

	}

	public CloudPath path(String first, String... more) {
		return CloudPath.of(first, more);
	}

}
