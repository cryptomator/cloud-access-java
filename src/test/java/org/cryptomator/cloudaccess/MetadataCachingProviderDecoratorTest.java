package org.cryptomator.cloudaccess;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <code>
 * path/to/root
 * ├─ dir1
 * │  ├─ dir2
 * │  └─ file3
 * ├─ file1
 * ├─ file2
 * ├─ file4
 * </code>
 */
public class MetadataCachingProviderDecoratorTest {

	private final CloudPath rootDir = CloudPath.of("");
	private final CloudItemMetadata dir1Metadata = new CloudItemMetadata("dir1", rootDir.resolve("dir1"), CloudItemType.FOLDER);
	private final CloudItemMetadata file1Metadata = new CloudItemMetadata("file1.jpg", rootDir.resolve("file1.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata file2Metadata = new CloudItemMetadata("file2.mp4", rootDir.resolve("file2.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata dir2Metadata = new CloudItemMetadata("dir2", rootDir.resolve("dir1/dir2"), CloudItemType.FOLDER);
	private final CloudItemMetadata file3Metadata = new CloudItemMetadata("file3.txt", rootDir.resolve("dir1/file3.txt"), CloudItemType.FILE);
	private final CloudItemMetadata file4Metadata = new CloudItemMetadata("file4.png", rootDir.resolve("file4.png"), CloudItemType.FILE);

	private CloudProvider cloudProvider;
	private MetadataCachingProviderDecorator decorator;

	@BeforeEach
	public void setup() {
		cloudProvider = Mockito.mock(CloudProvider.class);
		decorator = new MetadataCachingProviderDecorator(cloudProvider);
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from delegate")
	public void testItemMetadataOfFile3FromDelegate() {
		Mockito.when(cloudProvider.itemMetadata(file3Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(file3Metadata));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(file3Metadata.getName(), result.getName());
		Assertions.assertEquals(CloudItemType.FILE, result.getItemType());
		Assertions.assertEquals(file3Metadata.getPath(), result.getPath());
		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file3Metadata.getPath()).get(), file3Metadata);
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from delegate (not found)")
	public void testItemMetadataOfFile3FromDelegateNotFound() {
		Mockito.when(cloudProvider.itemMetadata(file3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		Assertions.assertThrows(NotFoundException.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().join()));

		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file3Metadata.getPath()), Optional.empty());
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from delegate (general error)")
	public void testItemMetadataOfFile3FromDelegateOtherException() {
		Mockito.when(cloudProvider.itemMetadata(file3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new CloudProviderException()));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		Assertions.assertThrows(CloudProviderException.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().join()));

		Assertions.assertNull(decorator.metadataCache.getIfPresent(file3Metadata.getPath()));
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from cache")
	public void testItemMetadataOfFile3FromCache() {
		decorator.metadataCache.put(file3Metadata.getPath(), Optional.of(file3Metadata));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(file3Metadata.getName(), result.getName());
		Assertions.assertEquals(CloudItemType.FILE, result.getItemType());
		Assertions.assertEquals(file3Metadata.getPath(), result.getPath());
		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file3Metadata.getPath()).get(), file3Metadata);
	}

	@Test
	@DisplayName("list(\"/\")")
	public void testListRoot() {
		var rootItemList = new CloudItemList(List.of(dir1Metadata, file1Metadata, file2Metadata, file4Metadata), Optional.empty());
		Mockito.when(cloudProvider.list(rootDir, Optional.empty())).thenReturn(CompletableFuture.completedFuture(rootItemList));

		var futureResult = decorator.list(rootDir, Optional.empty());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(4, result.getItems().size());
		var names = result.getItems().stream().map(CloudItemMetadata::getName).collect(Collectors.toSet());
		MatcherAssert.assertThat(names, CoreMatchers.hasItem(dir1Metadata.getName()));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem(file1Metadata.getName()));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem(file2Metadata.getName()));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem(file4Metadata.getName()));

		Assertions.assertEquals(decorator.metadataCache.getIfPresent(dir1Metadata.getPath()).get(), dir1Metadata);
		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file1Metadata.getPath()).get(), file1Metadata);
		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file2Metadata.getPath()).get(), file2Metadata);
		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file4Metadata.getPath()).get(), file4Metadata);
	}

	@Test
	@DisplayName("list(\"/Directory 1\") throws NotFoundException")
	public void testListdir1NotFound() {
		/*
		 * path/to/root
		 * ├─ dir1
		 * │  ├─ dir2
		 * │  └─ file3
		 ...
		 * ├─ file4
		 */
		decorator.metadataCache.put(dir1Metadata.getPath(), Optional.of(dir1Metadata));
		decorator.metadataCache.put(dir2Metadata.getPath(), Optional.of(dir2Metadata));
		decorator.metadataCache.put(file3Metadata.getPath(), Optional.of(file3Metadata));

		decorator.metadataCache.put(file4Metadata.getPath(), Optional.of(file4Metadata));

		Mockito.when(cloudProvider.list(dir1Metadata.getPath(), Optional.empty())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.list(dir1Metadata.getPath(), Optional.empty()).toCompletableFuture().join());

		Assertions.assertNull(decorator.metadataCache.getIfPresent(dir1Metadata.getPath()));
		Assertions.assertNull(decorator.metadataCache.getIfPresent(dir2Metadata.getPath()));

		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file4Metadata.getPath()).get(), file4Metadata);
	}

	@Test
	@DisplayName("read(\"/File 1\", NO_PROGRESS_AWARE)")
	public void testRead() throws IOException {
		var file1Content = "TOPSECRET!".getBytes();
		Mockito.when(cloudProvider.read(Mockito.eq(file1Metadata.getPath()), Mockito.any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new ByteArrayInputStream(file1Content)));
		var futureResult = decorator.read(file1Metadata.getPath(), ProgressListener.NO_PROGRESS_AWARE);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		byte[] buf = new byte[100];
		try (var in = result) {
			int read = in.read(buf);
			Assertions.assertEquals(10, read);
		}

		Assertions.assertArrayEquals("TOPSECRET!".getBytes(), Arrays.copyOf(buf, 10));
		Assertions.assertNull(decorator.metadataCache.getIfPresent(file1Metadata.getPath()));
	}

	@Test
	@DisplayName("read(\"/File 1\", NO_PROGRESS_AWARE) throws NotFoundException")
	public void testReadNotFound() {
		decorator.metadataCache.put(file1Metadata.getPath(), Optional.of(file1Metadata));

		Mockito.when(cloudProvider.read(Mockito.eq(file1Metadata.getPath()), Mockito.any())).thenAnswer(invocation -> CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.read(file1Metadata.getPath(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		Assertions.assertNull(decorator.metadataCache.getIfPresent(file1Metadata.getPath()));
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\", replace=false)")
	public void testMoveFileToNewFile() {
		decorator.metadataCache.put(file4Metadata.getPath(), Optional.of(file4Metadata));

		final var movedFile4Metadata = new CloudItemMetadata("file4.png", rootDir.resolve("dir1/file4.png"), CloudItemType.FILE);

		Mockito.when(cloudProvider.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false)).thenReturn(CompletableFuture.completedFuture(movedFile4Metadata.getPath()));

		var futureResult = decorator.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(movedFile4Metadata.getName(), result.getFileName().toString());
		Assertions.assertEquals(movedFile4Metadata.getPath(), result);

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\", replace=false) throws NotFoundException")
	public void testMoveFileToNewFileNotFound() {
		decorator.metadataCache.put(file4Metadata.getPath(), Optional.of(file4Metadata));

		final var movedFile4Metadata = new CloudItemMetadata("file4.png", rootDir.resolve("dir1/file4.png"), CloudItemType.FILE);

		Mockito.when(cloudProvider.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false)).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false).toCompletableFuture().join());

		Assertions.assertEquals(decorator.metadataCache.size(), 0L);
	}

	@Test
	@DisplayName("delete(\"/File 4\")")
	public void testDeleteFile() {
		decorator.metadataCache.put(file4Metadata.getPath(), Optional.of(file4Metadata));

		Mockito.when(cloudProvider.delete(file4Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.delete(file4Metadata.getPath());
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}

	@Test
	@DisplayName("delete(\"/File 4\") throws NotFoundException")
	public void testDeleteFileNotFound() {
		decorator.metadataCache.put(file4Metadata.getPath(), Optional.of(file4Metadata));

		Mockito.when(cloudProvider.delete(file4Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.delete(file4Metadata.getPath()).toCompletableFuture().join());

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}

	@Test
	@DisplayName("delete(\"/Directory 1\")")
	public void testDeleteFolder() {
		decorator.metadataCache.put(dir1Metadata.getPath(), Optional.of(dir1Metadata));

		Mockito.when(cloudProvider.delete(dir1Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.delete(dir1Metadata.getPath());
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}

	@Test
	@DisplayName("write(\"/File 1\", replace=false, text, NO_PROGRESS_AWARE)")
	public void testWriteToFile() {
		var updatedFile1Metadata = new CloudItemMetadata(file1Metadata.getName(), file1Metadata.getPath(), CloudItemType.FILE, Optional.of(Instant.EPOCH), Optional.of(15l));

		Mockito.when(cloudProvider.write(Mockito.eq(file1Metadata.getPath()), Mockito.eq(false), Mockito.any(InputStream.class), Mockito.eq(15l), Mockito.eq(Optional.empty()), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.completedFuture(updatedFile1Metadata));

		var futureResult = decorator.write(file1Metadata.getPath(), false, new ByteArrayInputStream("TOPSECRET!".getBytes(UTF_8)),15l, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(file1Metadata.getPath(), result.getPath());
		Assertions.assertEquals(file1Metadata.getName(), result.getName());
		Assertions.assertEquals(CloudItemType.FILE, result.getItemType());
		Assertions.assertEquals(Optional.of(15l), result.getSize());
		Assertions.assertEquals(Optional.of(Instant.EPOCH), result.getLastModifiedDate());

		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file1Metadata.getPath()).get(), updatedFile1Metadata);
	}

	@Test
	@DisplayName("write(\"/File 1\", replace=false, text, NO_PROGRESS_AWARE) throws NotFoundException")
	public void testWriteToFileNotFound() {
		decorator.metadataCache.put(file1Metadata.getPath(), Optional.of(file1Metadata));

		Mockito.when(cloudProvider.write(Mockito.eq(file1Metadata.getPath()), Mockito.eq(false), Mockito.any(InputStream.class), Mockito.eq(15l), Mockito.eq(Optional.empty()), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.write(file1Metadata.getPath(), false, new ByteArrayInputStream("TOPSECRET!".getBytes(UTF_8)),15l, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		Assertions.assertNull(decorator.metadataCache.getIfPresent(file1Metadata.getPath()));
	}

	@DisplayName("create(\"/Directory 3/\")")
	@Test
	public void testCreateFolder() {
		/*
		 * <code>
		 * path/to/root
		 * ├─ Directory 1
		 * │  ├─ ...
		 * ├─ Directory 3
		 * ├─ ...
		 * </code>
		 */
		final var dir3Metadata = new CloudItemMetadata("dir3", rootDir.resolve("dir1/dir3"), CloudItemType.FOLDER);

		Mockito.when(cloudProvider.createFolder(dir3Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(dir3Metadata.getPath()));
		var futureResult = decorator.createFolder(dir3Metadata.getPath());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(dir3Metadata.getName(), result.getFileName().toString());
		Assertions.assertEquals(dir3Metadata.getPath(), result);

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}

	@DisplayName("create(\"/Directory 3/\") throws NotFoundException")
	@Test
	public void testCreateFolderNotFound() {
		final var dir3Metadata = new CloudItemMetadata("dir3", rootDir.resolve("dir1/dir3"), CloudItemType.FOLDER);

		decorator.metadataCache.put(dir3Metadata.getPath(), Optional.of(dir3Metadata));

		Mockito.when(cloudProvider.createFolder(dir3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.createFolder(dir3Metadata.getPath()).toCompletableFuture().join());

		Assertions.assertEquals(decorator.metadataCache.size(), 0l);
	}


	@DisplayName("invalidate cache entry after duration expired")
	@Test
	public void testAutoInvalidateCache() throws InterruptedException {
		decorator = new MetadataCachingProviderDecorator(cloudProvider, Duration.ofMillis(100));

		decorator.metadataCache.put(file1Metadata.getPath(), Optional.of(file1Metadata));

		Assertions.assertEquals(decorator.metadataCache.getIfPresent(file1Metadata.getPath()).get(), file1Metadata);

		Thread.sleep(200);

		Assertions.assertNull(decorator.metadataCache.getIfPresent(file1Metadata.getPath()));
	}

}