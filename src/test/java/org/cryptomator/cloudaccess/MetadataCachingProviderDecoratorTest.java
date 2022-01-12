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
import java.util.concurrent.ExecutionException;
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
	private final CloudItemMetadata file1Metadata = new CloudItemMetadata("file1.jpg", rootDir.resolve("file1.jpg"), CloudItemType.FILE);
	private final CloudItemMetadata file2Metadata = new CloudItemMetadata("file2.mp4", rootDir.resolve("file2.mp4"), CloudItemType.FILE);
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
		Assertions.assertEquals(file3Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file3Metadata.getPath()).toCompletableFuture().join());
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from delegate (not found)")
	public void testItemMetadataOfFile3FromDelegateNotFound() {
		Mockito.when(cloudProvider.itemMetadata(file3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		Assertions.assertThrows(NotFoundException.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().join()));
		var futureMetadata = decorator.cachedItemMetadataRequests.getIfPresent(file3Metadata.getPath());
		var futureMetadataException = Assertions.assertThrows(ExecutionException.class, () -> futureMetadata.toCompletableFuture().get());

		MatcherAssert.assertThat(futureMetadataException.getCause(), CoreMatchers.instanceOf(NotFoundException.class));
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from delegate (general error)")
	public void testItemMetadataOfFile3FromDelegateOtherException() {
		Mockito.when(cloudProvider.itemMetadata(file3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new CloudProviderException()));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		Assertions.assertThrows(CloudProviderException.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().join()));
		var futureMetadata = decorator.cachedItemMetadataRequests.getIfPresent(file3Metadata.getPath());
		var futureMetadataException = Assertions.assertThrows(ExecutionException.class, () -> futureMetadata.toCompletableFuture().get());

		MatcherAssert.assertThat(futureMetadataException.getCause(), CoreMatchers.instanceOf(CloudProviderException.class));
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\") from cache")
	public void testItemMetadataOfFile3FromCache() {
		decorator.cachedItemMetadataRequests.put(file3Metadata.getPath(), CompletableFuture.completedFuture(file3Metadata));

		var futureResult = decorator.itemMetadata(file3Metadata.getPath());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(file3Metadata.getName(), result.getName());
		Assertions.assertEquals(CloudItemType.FILE, result.getItemType());
		Assertions.assertEquals(file3Metadata.getPath(), result.getPath());
		Assertions.assertEquals(file3Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file3Metadata.getPath()).toCompletableFuture().join());
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

		Assertions.assertEquals(dir1Metadata, decorator.cachedItemMetadataRequests.getIfPresent(dir1Metadata.getPath()).toCompletableFuture().join());
		Assertions.assertEquals(file1Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()).toCompletableFuture().join());
		Assertions.assertEquals(file2Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file2Metadata.getPath()).toCompletableFuture().join());
		Assertions.assertEquals(file4Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file4Metadata.getPath()).toCompletableFuture().join());
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
		decorator.cachedItemMetadataRequests.put(dir1Metadata.getPath(), CompletableFuture.completedFuture(dir1Metadata));
		decorator.cachedItemMetadataRequests.put(dir2Metadata.getPath(), CompletableFuture.completedFuture(dir2Metadata));
		decorator.cachedItemMetadataRequests.put(file3Metadata.getPath(), CompletableFuture.completedFuture(file3Metadata));

		decorator.cachedItemMetadataRequests.put(file4Metadata.getPath(), CompletableFuture.completedFuture(file4Metadata));

		Mockito.when(cloudProvider.list(dir1Metadata.getPath(), Optional.empty())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.list(dir1Metadata.getPath(), Optional.empty()).toCompletableFuture().join());

		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(dir1Metadata.getPath()));
		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(dir2Metadata.getPath()));

		Assertions.assertEquals(file4Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file4Metadata.getPath()).toCompletableFuture().join());
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
		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()));
	}

	@Test
	@DisplayName("read(\"/File 1\", NO_PROGRESS_AWARE) throws NotFoundException")
	public void testReadNotFound() {
		decorator.cachedItemMetadataRequests.put(file1Metadata.getPath(), CompletableFuture.completedFuture(file1Metadata));

		Mockito.when(cloudProvider.read(Mockito.eq(file1Metadata.getPath()), Mockito.any())).thenAnswer(invocation -> CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.read(file1Metadata.getPath(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()));
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\", replace=false)")
	public void testMoveFileToNewFile() {
		decorator.cachedItemMetadataRequests.put(file4Metadata.getPath(), CompletableFuture.completedFuture(file4Metadata));

		final var movedFile4Metadata = new CloudItemMetadata("file4.png", rootDir.resolve("dir1/file4.png"), CloudItemType.FILE);

		Mockito.when(cloudProvider.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false)).thenReturn(CompletableFuture.completedFuture(movedFile4Metadata.getPath()));

		var futureResult = decorator.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(movedFile4Metadata.getName(), result.getFileName().toString());
		Assertions.assertEquals(movedFile4Metadata.getPath(), result);

		Assertions.assertEquals(0l, decorator.cachedItemMetadataRequests.size());
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\", replace=false) throws NotFoundException")
	public void testMoveFileToNewFileNotFound() {
		decorator.cachedItemMetadataRequests.put(file4Metadata.getPath(), CompletableFuture.completedFuture(file4Metadata));

		final var movedFile4Metadata = new CloudItemMetadata("file4.png", rootDir.resolve("dir1/file4.png"), CloudItemType.FILE);

		Mockito.when(cloudProvider.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false)).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.move(file4Metadata.getPath(), movedFile4Metadata.getPath(), false).toCompletableFuture().join());

		Assertions.assertEquals(0L, decorator.cachedItemMetadataRequests.size());
	}

	@Test
	@DisplayName("delete(\"/File 4\")")
	public void testDeleteFile() {
		decorator.cachedItemMetadataRequests.put(file4Metadata.getPath(), CompletableFuture.completedFuture(file4Metadata));

		Mockito.when(cloudProvider.deleteFile(file4Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.deleteFile(file4Metadata.getPath());
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());
		var futureMetadata = decorator.cachedItemMetadataRequests.getIfPresent(file4Metadata.getPath());
		var futureMetadataException = Assertions.assertThrows(ExecutionException.class, () -> futureMetadata.toCompletableFuture().get());

		Assertions.assertEquals(1l, decorator.cachedItemMetadataRequests.size());
		MatcherAssert.assertThat(futureMetadataException.getCause(), CoreMatchers.instanceOf(NotFoundException.class));
	}

	@Test
	@DisplayName("delete(\"/File 4\") throws NotFoundException")
	public void testDeleteFileNotFound() {
		decorator.cachedItemMetadataRequests.put(file4Metadata.getPath(), CompletableFuture.completedFuture(file4Metadata));

		Mockito.when(cloudProvider.deleteFile(file4Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));
		Assertions.assertThrows(NotFoundException.class, () -> decorator.deleteFile(file4Metadata.getPath()).toCompletableFuture().join());
		var futureMetadata = decorator.cachedItemMetadataRequests.getIfPresent(file4Metadata.getPath());
		var futureMetadataException = Assertions.assertThrows(ExecutionException.class, () -> futureMetadata.toCompletableFuture().get());

		Assertions.assertEquals(1l, decorator.cachedItemMetadataRequests.size());
		MatcherAssert.assertThat(futureMetadataException.getCause(), CoreMatchers.instanceOf(NotFoundException.class));
	}

	@Test
	@DisplayName("delete(\"/Directory 1\")")
	public void testDeleteFolder() {
		decorator.cachedItemMetadataRequests.put(dir1Metadata.getPath(), CompletableFuture.completedFuture(dir1Metadata));

		Mockito.when(cloudProvider.deleteFolder(dir1Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.deleteFolder(dir1Metadata.getPath());
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());
		var futureMetadata = decorator.cachedItemMetadataRequests.getIfPresent(dir1Metadata.getPath());
		var futureMetadataException = Assertions.assertThrows(ExecutionException.class, () -> futureMetadata.toCompletableFuture().get());

		Assertions.assertEquals(1l, decorator.cachedItemMetadataRequests.size());
		MatcherAssert.assertThat(futureMetadataException.getCause(), CoreMatchers.instanceOf(NotFoundException.class));
	}

	@Test
	@DisplayName("write(\"/File 1\", replace=false, text, NO_PROGRESS_AWARE)")
	public void testWriteToFile() {
		var updatedFile1Metadata = new CloudItemMetadata(file1Metadata.getName(), file1Metadata.getPath(), CloudItemType.FILE, Optional.of(Instant.EPOCH), Optional.of(15l));

		Mockito.when(cloudProvider.write(Mockito.eq(file1Metadata.getPath()), Mockito.eq(false), Mockito.any(InputStream.class), Mockito.eq(15l), Mockito.eq(Optional.of(Instant.EPOCH)), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.write(file1Metadata.getPath(), false, new ByteArrayInputStream("TOPSECRET!".getBytes(UTF_8)), 15l, Optional.of(Instant.EPOCH), ProgressListener.NO_PROGRESS_AWARE);
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(1l, decorator.cachedItemMetadataRequests.size());
		Assertions.assertEquals(updatedFile1Metadata, decorator.cachedItemMetadataRequests.getIfPresent(updatedFile1Metadata.getPath()).toCompletableFuture().join());
	}

	@Test
	@DisplayName("write(\"/File 1\", replace=false, text, NO_PROGRESS_AWARE) throws NotFoundException")
	public void testWriteToFileNotFound() {
		decorator.cachedItemMetadataRequests.put(file1Metadata.getPath(), CompletableFuture.completedFuture(file1Metadata));

		Mockito.when(cloudProvider.write(Mockito.eq(file1Metadata.getPath()), Mockito.eq(false), Mockito.any(InputStream.class), Mockito.eq(15l), Mockito.eq(Optional.empty()), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.write(file1Metadata.getPath(), false, new ByteArrayInputStream("TOPSECRET!".getBytes(UTF_8)), 15l, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()));
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

		Assertions.assertEquals(0l, decorator.cachedItemMetadataRequests.size());
	}

	@DisplayName("create(\"/Directory 3/\") throws NotFoundException")
	@Test
	public void testCreateFolderNotFound() {
		final var dir3Metadata = new CloudItemMetadata("dir3", rootDir.resolve("dir1/dir3"), CloudItemType.FOLDER);

		decorator.cachedItemMetadataRequests.put(dir3Metadata.getPath(), CompletableFuture.completedFuture(dir3Metadata));

		Mockito.when(cloudProvider.createFolder(dir3Metadata.getPath())).thenReturn(CompletableFuture.failedFuture(new NotFoundException()));

		Assertions.assertThrows(NotFoundException.class, () -> decorator.createFolder(dir3Metadata.getPath()).toCompletableFuture().join());

		Assertions.assertEquals(0l, decorator.cachedItemMetadataRequests.size());
	}


	@DisplayName("invalidate cache entry after duration expired")
	@Test
	public void testAutoInvalidateCache() throws InterruptedException {
		decorator = new MetadataCachingProviderDecorator(cloudProvider, Duration.ofMillis(100));

		decorator.cachedItemMetadataRequests.put(file1Metadata.getPath(), CompletableFuture.completedFuture(file1Metadata));

		Assertions.assertEquals(file1Metadata, decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()).toCompletableFuture().join());

		Thread.sleep(200);

		Assertions.assertNull(decorator.cachedItemMetadataRequests.getIfPresent(file1Metadata.getPath()));
	}

}