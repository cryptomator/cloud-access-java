package org.cryptomator.cloudaccess.localfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Collectors;

import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalFsCloudProviderTest {

	private Path root;
	private CloudProvider provider;

	@BeforeEach
	public void setup(@TempDir Path tempDir) {
		this.root = tempDir;
		this.provider = new LocalFsCloudProvider(tempDir);
	}

	@Test
	public void testItemMetadata() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.itemMetadata(Path.of("/file"));
		var metaData = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get());

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(Path.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(11, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());

	}

	@Test
	public void testListExhaustively() throws IOException {
		Files.createDirectory(root.resolve("dir"));
		Files.createFile(root.resolve("file"));

		var result = provider.listExhaustively(Path.of("/"));
		var itemList = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get());
		
		Assertions.assertFalse(itemList.getNextPageToken().isPresent());
		MatcherAssert.assertThat(itemList.getItems().stream().map(CloudItemMetadata::getPath).collect(Collectors.toSet()), CoreMatchers.hasItems(Path.of("/file"), Path.of("/dir")));
	}

	@Test
	public void testRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(Path.of("/file"), ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("hello world".getBytes(), allBytes);
		}
	}

	@Test
	public void testRandomAccessRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(Path.of("/file"), 4, 3, ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("o w".getBytes(), allBytes);
		}
	}
	
	@Test
	public void testCreateFolder() {
		var result = provider.createFolder(Path.of("/folder"));
		var folder = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get());
		
		Assertions.assertEquals(Path.of("/folder"), folder);
		Assertions.assertTrue(Files.isDirectory(root.resolve("folder")));
	}
}
