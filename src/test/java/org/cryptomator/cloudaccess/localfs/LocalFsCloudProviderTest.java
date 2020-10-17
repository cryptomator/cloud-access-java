package org.cryptomator.cloudaccess.localfs;

import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class LocalFsCloudProviderTest {

	private Path root;
	private CloudProvider provider;

	@BeforeEach
	public void setup(@TempDir Path tempDir) {
		this.root = tempDir;
		this.provider = new LocalFsCloudProvider(tempDir);
	}

	@Test
	@DisplayName("get metadata of /file")
	public void testItemMetadata() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.itemMetadata(CloudPath.of("/file"));
		var metaData = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(CloudPath.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(11, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}

	@Test
	@DisplayName("quota /")
	public void testQuota() throws IOException {
		var result = provider.quota(CloudPath.of("/"));
		var quota = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertTrue(quota.getTotalBytes().isPresent());
		Assertions.assertTrue(quota.getUsedBytes().isEmpty());
	}

	@Test
	@DisplayName("list /")
	public void testListExhaustively() throws IOException {
		Files.createDirectory(root.resolve("dir"));
		Files.createFile(root.resolve("file"));

		var result = provider.listExhaustively(CloudPath.of("/"));
		var itemList = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertFalse(itemList.getNextPageToken().isPresent());
		MatcherAssert.assertThat(itemList.getItems().stream().map(CloudItemMetadata::getPath).collect(Collectors.toSet()), CoreMatchers.hasItems(CloudPath.of("/file"), CloudPath.of("/dir")));
	}

	@Test
	@DisplayName("read /file (complete)")
	public void testRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(CloudPath.of("/file"), ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("hello world".getBytes(), allBytes);
		}
	}

	@Test
	@DisplayName("read /file (bytes 4-6)")
	public void testRandomAccessRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(CloudPath.of("/file"), 4, 3, ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("o w".getBytes(), allBytes);
		}
	}

	@Test
	@DisplayName("write to /file (non-existing)")
	public void testWriteToNewFile() throws IOException {
		var in = new ByteArrayInputStream("hallo welt".getBytes());

		var result = provider.write(CloudPath.of("/file"), false, in, 10, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		var metaData = provider.itemMetadata(CloudPath.of("/file")).toCompletableFuture().join();

		Assertions.assertEquals(CloudPath.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(10, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}

	@Test
	@DisplayName("write to /file (already existing)")
	public void testWriteToExistingFile() throws IOException, ExecutionException, InterruptedException {
		Files.write(root.resolve("file"), "hello world".getBytes());
		var in = new ByteArrayInputStream("hallo welt".getBytes());

		var result = provider.write(CloudPath.of("/file"), false, in, 10, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);

		Assertions.assertThrows(AlreadyExistsException.class, () -> {
			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().join());
		});
	}

	@Test
	@DisplayName("write to /file (replace existing)")
	public void testWriteToAndReplaceExistingFile() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());
		var in = new ByteArrayInputStream("hallo welt".getBytes());

		var result = provider.write(CloudPath.of("/file"), true, in, 10, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		var metaData = provider.itemMetadata(CloudPath.of("/file")).toCompletableFuture().join();

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(CloudPath.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(10, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}


	@Test
	@DisplayName("write to /file (non-existing) update modification date")
	public void testWriteToNewFileUpdateModificationDate() throws IOException {
		var in = new ByteArrayInputStream("hallo welt".getBytes());

		// Files.getLastModifiedTime(...) precision in Windows is ChronoUnit.MICROS and Instant.now() is ChronoUnit.NANOS
		var modDate = Instant.now().minus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);

		var result = provider.write(CloudPath.of("/file"), false, in, 10, Optional.of(modDate), ProgressListener.NO_PROGRESS_AWARE);
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		var metaData = provider.itemMetadata(CloudPath.of("/file")).toCompletableFuture().join();

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(CloudPath.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(10, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), modDate);
	}

	@Test
	@DisplayName("create /folder")
	public void testCreateFolder() {
		var result = provider.createFolder(CloudPath.of("/folder"));
		var folder = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(CloudPath.of("/folder"), folder);
		Assertions.assertTrue(Files.isDirectory(root.resolve("folder")));
	}

	@Test
	@DisplayName("delete /file")
	public void testDeleteFile() throws IOException {
		Files.createFile(root.resolve("file"));

		var result = provider.deleteFile(CloudPath.of("/file"));
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertTrue(Files.notExists(root.resolve("file")));
	}

	@Test
	@DisplayName("delete /folder (recursively)")
	public void testDeleteFolder() throws IOException {
		Files.createDirectory(root.resolve("folder"));
		Files.createFile(root.resolve("folder/file"));

		var result = provider.deleteFolder(CloudPath.of("/folder"));
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertTrue(Files.notExists(root.resolve("folder")));
	}

	@Test
	@DisplayName("move /foo -> /bar (non-existing)")
	public void testMoveToNonExisting() throws IOException {
		Files.createFile(root.resolve("foo"));

		var result = provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false);
		var moved = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(CloudPath.of("/bar"), moved);
		Assertions.assertTrue(Files.notExists(root.resolve("foo")));
		Assertions.assertTrue(Files.exists(root.resolve("bar")));
	}

	@Test
	@DisplayName("move /foo -> /bar (already exists)")
	public void testMoveToExisting() throws IOException {
		Files.createFile(root.resolve("foo"));
		Files.createFile(root.resolve("bar"));

		var result = provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false);
		Assertions.assertThrows(AlreadyExistsException.class, () -> {
			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().join());
		});
	}

	@Test
	@DisplayName("move /foo -> /bar (replace existing)")
	public void testMoveToAndReplaceExisting() throws IOException {
		Files.createFile(root.resolve("foo"));
		Files.createFile(root.resolve("bar"));

		var result = provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), true);
		var moved = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(CloudPath.of("/bar"), moved);
		Assertions.assertTrue(Files.notExists(root.resolve("foo")));
		Assertions.assertTrue(Files.exists(root.resolve("bar")));
	}
}
