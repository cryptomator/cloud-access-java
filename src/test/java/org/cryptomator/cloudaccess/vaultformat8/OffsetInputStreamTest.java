package org.cryptomator.cloudaccess.vaultformat8;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

public class OffsetInputStreamTest {

	public static byte[] TEST_DATA = new byte[256];

	@BeforeAll
	public static void setupOriginal() {
		IntStream.range(0, TEST_DATA.length).forEach(i -> {
			TEST_DATA[i] = (byte) i;
		});
	}

	@ParameterizedTest(name = "offset by {0}")
	@DisplayName("skip to/behind EOF")
	@ValueSource(ints = {256, 257, 300})
	public void testEof(int offset) throws IOException {
		Assumptions.assumeTrue(offset >= TEST_DATA.length);
		var original = new ByteArrayInputStream(TEST_DATA);

		try (var in = new OffsetInputStream(original, offset)) {
			Assertions.assertEquals(-1, in.read());
			Assertions.assertEquals(-1, in.read(new byte[1]));
			Assertions.assertArrayEquals(new byte[0], in.readAllBytes());
		}
	}

	@DisplayName("read()")
	@ParameterizedTest(name = "offset by {0}")
	@ValueSource(ints = {0, 1, 2, 10, 20, 254})
	public void testRead(int offset) throws IOException {
		Assumptions.assumeTrue(offset < TEST_DATA.length);
		var original = new ByteArrayInputStream(TEST_DATA);

		try (var in = new OffsetInputStream(original, offset)) {
			Assertions.assertEquals(TEST_DATA[offset], (byte) in.read());
		}
	}

	@DisplayName("read(buf, off, len)")
	@ParameterizedTest(name = "offset by {0}")
	@ValueSource(ints = {0, 1, 2, 10, 20})
	public void testReadMultiple(int offset) throws IOException {
		Assumptions.assumeTrue(offset < TEST_DATA.length);
		var original = new ByteArrayInputStream(TEST_DATA);

		byte[] buf = new byte[5];
		try (var in = new OffsetInputStream(original, offset)) {
			in.read(buf, 0, buf.length);
		}
		Assertions.assertArrayEquals(Arrays.copyOfRange(TEST_DATA, offset, offset + buf.length), buf);
	}

	@DisplayName("skip(n)")
	@ParameterizedTest(name = "offset by {0}")
	@ValueSource(ints = {0, 1, 2, 10, 20})
	public void testSkip(int offset) throws IOException {
		Assumptions.assumeTrue(offset < TEST_DATA.length);
		var original = new ByteArrayInputStream(TEST_DATA);

		try (var in = new OffsetInputStream(original, offset)) {
			in.skip(3);
			Assertions.assertEquals(TEST_DATA[offset + 3], in.read());
		}
	}

	@DisplayName("readAllBytes() to EOF")
	@ParameterizedTest(name = "offset by {0}")
	@ValueSource(ints = {0, 1, 2, 10, 20})
	public void testReadAllBytes(int offset) throws IOException {
		Assumptions.assumeTrue(offset < TEST_DATA.length);
		var original = new ByteArrayInputStream(TEST_DATA);

		try (var in = new OffsetInputStream(original, offset)) {
			byte[] buf = in.readAllBytes();
			Assertions.assertArrayEquals(Arrays.copyOfRange(TEST_DATA, offset, TEST_DATA.length), buf);
		}
	}

}