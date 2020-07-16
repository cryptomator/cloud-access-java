package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.io.BaseEncoding;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileNameCryptor;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * <code>
 * path/to/vault/d
 * ├─ Directory 1
 * │  ├─ Directory 2
 * │  └─ File 3
 * ├─ File 1
 * ├─ File 2
 * </code>
 */
public class VaultFormat8ProviderDecoratorTest {

	private final Path dataDir = Path.of("path/to/vault/d");
	private final String dirIdRoot = "";
	private final String dirId1 = "dir1-id";
	private final CloudItemMetadata dir1Metadata = new CloudItemMetadata("dir1.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r"), CloudItemType.FOLDER);
	private final CloudItemMetadata file1Metadata = new CloudItemMetadata("file1.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata file2Metadata = new CloudItemMetadata("file2.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata other1Metadata = new CloudItemMetadata("other.txt", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/other.txt"), CloudItemType.FILE);
	private final CloudItemMetadata dir2Metadata = new CloudItemMetadata("dir2.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r"), CloudItemType.FOLDER);
	private final CloudItemMetadata file3Metadata = new CloudItemMetadata("file3.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file3.c9r"), CloudItemType.FILE);

	private CloudProvider cloudProvider;
	private Cryptor cryptor;
	private FileNameCryptor fileNameCryptor;
	private VaultFormat8ProviderDecorator decorator;

	@BeforeEach
	public void setup() {
		cloudProvider = Mockito.mock(CloudProvider.class);
		cryptor = Mockito.mock(Cryptor.class);
		fileNameCryptor = Mockito.mock(FileNameCryptor.class);
		decorator = new VaultFormat8ProviderDecorator(cloudProvider, dataDir, cryptor);

		Mockito.when(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor);
		Mockito.when(fileNameCryptor.hashDirectoryId(dirIdRoot)).thenReturn("00AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId1)).thenReturn("11BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir1", dirIdRoot.getBytes())).thenReturn("Directory 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file1", dirIdRoot.getBytes())).thenReturn("File 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file2", dirIdRoot.getBytes())).thenReturn("File 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir2", dirId1.getBytes())).thenReturn("Directory 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file3", dirId1.getBytes())).thenReturn("File 3");
	}

	@Test
	@DisplayName("itemMetadata(\"Directory 1/File 3\")")
	public void testItemMetadata() {
		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file3.c9r"))).thenReturn(CompletableFuture.completedFuture(file3Metadata));
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 3", dirId1.getBytes())).thenReturn("file3");

		var futureResult = decorator.itemMetadata(Path.of("/Directory 1/File 3"));
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals("File 3", result.getName());
		Assertions.assertEquals(CloudItemType.FILE, result.getItemType());
		Assertions.assertEquals(Path.of("/Directory 1/File 3"), result.getPath());
	}

	@Test
	@DisplayName("list(\"/\")")
	public void testListRoot() {
		var rootItemList = new CloudItemList(List.of(dir1Metadata, file1Metadata, file2Metadata, other1Metadata), Optional.empty());
		Mockito.when(cloudProvider.list(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), Optional.empty())).thenReturn(CompletableFuture.completedFuture(rootItemList));

		var futureResult = decorator.list(Path.of("/"), Optional.empty());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(3, result.getItems().size());
		var names = result.getItems().stream().map(CloudItemMetadata::getName).collect(Collectors.toSet());
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("Directory 1"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 1"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 2"));
	}

	@Test
	@DisplayName("list(\"/Directory 1/\")")
	public void testListDir1() {
		var dir1ItemList = new CloudItemList(List.of(dir2Metadata, file3Metadata), Optional.empty());
		Mockito.when(cloudProvider.list(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"), Optional.empty())).thenReturn(CompletableFuture.completedFuture(dir1ItemList));
		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");

		var futureResult = decorator.list(Path.of("/Directory 1"), Optional.empty());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(2, result.getItems().size());
		var names = result.getItems().stream().map(CloudItemMetadata::getName).collect(Collectors.toSet());
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("Directory 2"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 3"));
	}

}