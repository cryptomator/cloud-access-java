package org.cryptomator.cloudaccess.vaultformat8;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

class DirectoryIdCacheTest {

	private DirectoryIdCache cache;

	@BeforeEach
	public void setup() {
		cache = new DirectoryIdCache();
	}

	@ParameterizedTest(name = "get(\"{0}\")")
	@ValueSource(strings = {"", "/", "//"})
	public void testContainsRoot(String path) {
		var onMiss = Mockito.mock(BiFunction.class);
		CompletionStage<byte[]> future = cache.get(Path.of(path), onMiss);

		var cached = Assertions.assertTimeoutPreemptively(Duration.ofMillis(1000), () -> future.toCompletableFuture().get());

		Assertions.assertArrayEquals(new byte[0], cached);
		Mockito.verify(onMiss, Mockito.never()).apply(Mockito.any(), Mockito.any());
	}

	@Test
	public void testRecursiveGet() {
		var onMiss = Mockito.mock(BiFunction.class);
		Mockito.when(onMiss.apply(Path.of("/one"), new byte[0])).thenReturn(CompletableFuture.completedFuture(new byte[1]));
		Mockito.when(onMiss.apply(Path.of("/one/two"), new byte[1])).thenReturn(CompletableFuture.completedFuture(new byte[2]));
		Mockito.when(onMiss.apply(Path.of("/one/two/three"), new byte[2])).thenReturn(CompletableFuture.completedFuture(new byte[3]));

		CompletionStage<byte[]> future = cache.get(Path.of("/one/two/three"), onMiss);

		var cached = Assertions.assertTimeoutPreemptively(Duration.ofMillis(1000), () -> future.toCompletableFuture().get());

		Assertions.assertArrayEquals(new byte[3], cached);
		Mockito.verify(onMiss).apply(Path.of("/one"), new byte[0]);
		Mockito.verify(onMiss).apply(Path.of("/one/two"), new byte[1]);
		Mockito.verify(onMiss).apply(Path.of("/one/two/three"), new byte[2]);
	}

}