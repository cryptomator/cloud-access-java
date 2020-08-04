package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.io.BaseEncoding;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileContentCryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.cryptomator.cryptolib.api.FileHeaderCryptor;
import org.cryptomator.cryptolib.api.FileNameCryptor;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * <code>
 * path/to/vault/d
 * ├─ Directory 1
 * │  ├─ Directory 2
 * │  └─ File 3
 * ├─ File 1
 * ├─ File 2
 * ├─ File 4
 * </code>
 */
public class VaultFormat8ProviderDecoratorTest {

	private final Path dataDir = Path.of("path/to/vault/d");
	private final String dirIdRoot = "";
	private final String dirId1 = "dir1-id";
	private final String dirId2 = "dir2-id";
	private final CloudItemMetadata dir1Metadata = new CloudItemMetadata("dir1.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r"), CloudItemType.FOLDER);
	private final CloudItemMetadata file1Metadata = new CloudItemMetadata("file1.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata file2Metadata = new CloudItemMetadata("file2.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata other1Metadata = new CloudItemMetadata("other.txt", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/other.txt"), CloudItemType.FILE);
	private final CloudItemMetadata dir2Metadata = new CloudItemMetadata("dir2.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r"), CloudItemType.FOLDER);
	private final CloudItemMetadata file3Metadata = new CloudItemMetadata("file3.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file3.c9r"), CloudItemType.FILE);
	private final CloudItemMetadata file4Metadata = new CloudItemMetadata("file4.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"), CloudItemType.FILE);
	private final TemporaryFolder tempFolder = new TemporaryFolder();

	private CloudProvider cloudProvider;
	private Cryptor cryptor;
	private FileNameCryptor fileNameCryptor;
	private FileContentCryptor fileContentCryptor;
	private FileHeaderCryptor fileHeaderCryptor;
	private VaultFormat8ProviderDecorator decorator;
	private Path tempDir;

	@BeforeEach
	public void setup() throws IOException {
		tempFolder.create();
		tempDir = tempFolder.newFolder("tempDir").toPath();

		cloudProvider = Mockito.mock(CloudProvider.class);
		cryptor = Mockito.mock(Cryptor.class);
		fileNameCryptor = Mockito.mock(FileNameCryptor.class);
		fileContentCryptor = Mockito.mock(FileContentCryptor.class);
		fileHeaderCryptor = Mockito.mock(FileHeaderCryptor.class);
		decorator = new VaultFormat8ProviderDecorator(cloudProvider, dataDir, tempDir, cryptor);

		Mockito.when(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor);
		Mockito.when(cryptor.fileContentCryptor()).thenReturn(fileContentCryptor);
		Mockito.when(cryptor.fileHeaderCryptor()).thenReturn(fileHeaderCryptor);
		Mockito.when(fileNameCryptor.hashDirectoryId(dirIdRoot)).thenReturn("00AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId1)).thenReturn("11BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId2)).thenReturn("22CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir1", dirIdRoot.getBytes())).thenReturn("Directory 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file1", dirIdRoot.getBytes())).thenReturn("File 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file2", dirIdRoot.getBytes())).thenReturn("File 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir2", dirId1.getBytes())).thenReturn("Directory 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file3", dirId1.getBytes())).thenReturn("File 3");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file4", dirIdRoot.getBytes())).thenReturn("File 4");
	}

	@AfterEach
	public void tearDown() {
		tempFolder.delete();
	}

	@Test
	@DisplayName("itemMetadata(\"/\")")
	public void testItemMetadataOfRoot() {
		var futureResult = decorator.itemMetadata(Path.of("/"));
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals("", result.getName());
		Assertions.assertEquals(CloudItemType.FOLDER, result.getItemType());
		Assertions.assertEquals(Path.of("/"), result.getPath());
	}

	@Test
	@DisplayName("itemMetadata(\"/Directory 1/File 3\")")
	public void testItemMetadataOfFile3() {
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
		var rootItemList = new CloudItemList(List.of(dir1Metadata, file1Metadata, file2Metadata, file4Metadata, other1Metadata), Optional.empty());
		Mockito.when(cloudProvider.list(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), Optional.empty())).thenReturn(CompletableFuture.completedFuture(rootItemList));

		var futureResult = decorator.list(Path.of("/"), Optional.empty());
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(4, result.getItems().size());
		var names = result.getItems().stream().map(CloudItemMetadata::getName).collect(Collectors.toSet());
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("Directory 1"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 1"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 2"));
		MatcherAssert.assertThat(names, CoreMatchers.hasItem("File 4"));
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

	@Test
	@DisplayName("read(\"/File 1\" 12, 10, NO_PROGRESS_AWARE)")
	public void testRead() throws IOException {
		var file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".getBytes();
		var header = Mockito.mock(FileHeader.class);
		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(8);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);
		Mockito.when(cloudProvider.read(Mockito.eq(file1Metadata.getPath()), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(invocation -> {
			long offset = invocation.getArgument(1);
			long length = invocation.getArgument(2);
			return CompletableFuture.completedFuture(new ByteArrayInputStream(file1Content, (int) offset, (int) length));
		});
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.getBytes())).thenReturn("file1");
		Mockito.when(fileHeaderCryptor.decryptHeader(UTF_8.encode("hhhhh"))).thenReturn(header);
		Mockito.when(fileContentCryptor.decryptChunk(Mockito.eq(UTF_8.encode("TOPSECRET!")), Mockito.anyLong(), Mockito.eq(header), Mockito.anyBoolean())).then(invocation -> UTF_8.encode("geheim!!"));

		var futureResult = decorator.read(Path.of("/File 1"), 12, 10, ProgressListener.NO_PROGRESS_AWARE);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		byte[] buf = new byte[100];
		try (var in = result) {
			int read = in.read(buf);
			Assertions.assertEquals(10, read);
		}
		// geheim!!geheim!!geheim!!.substr(12, 10)
		Assertions.assertArrayEquals("im!!geheim".getBytes(), Arrays.copyOf(buf, 10));
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\", replace=false)")
	public void testMoveFileToNewFile() {
		final CloudItemMetadata movedFile4Metadata = new CloudItemMetadata("file4.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4.c9r"), CloudItemType.FILE);

		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"))).thenReturn(CompletableFuture.completedFuture(file4Metadata));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4.c9r"))).thenReturn(CompletableFuture.completedFuture(movedFile4Metadata));

		Mockito.when(cloudProvider.move(Path.of("path/to/vault/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"), Path.of("path/to/vault/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4.c9r"), false)).thenReturn(CompletableFuture.completedFuture(Path.of("/Directory 1/File 4")));
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirId1.getBytes())).thenReturn("file4");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");

		var futureResult = decorator.move(Path.of("/File 4"), Path.of("/Directory 1/File 4"), false);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals("File 4", result.getFileName().toString());
		Assertions.assertEquals(Path.of("/Directory 1/File 4"), result);
	}

	@Test
	@DisplayName("delete(\"/File 4\")")
	public void testDeleteFile() {
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");

		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"))).thenReturn(CompletableFuture.completedFuture(file4Metadata));
		Mockito.when(cloudProvider.delete(Path.of("path/to/vault/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"))).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.delete(Path.of("/File 4"));
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());
	}

	@Test
	@DisplayName("delete(\"/Directory 1/Directory 2/\")")
	public void testDeleteSingleFolder() {
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.getBytes())).thenReturn("dir2");

		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(cloudProvider.read(dir2Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId2.getBytes())));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r"))).thenReturn(CompletableFuture.completedFuture(dir2Metadata));

		var dir2ItemList = new CloudItemList(List.of(), Optional.empty());
		Mockito.when(cloudProvider.listExhaustively(dataDir.resolve("22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"))).thenReturn(CompletableFuture.completedFuture(dir2ItemList));
		Mockito.when(cloudProvider.delete(dataDir.resolve("22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"))).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.delete(Path.of("/Directory 1/Directory 2/"));
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());
	}

	@Test
	@DisplayName("delete(\"/\")")
	public void testDeleteFolder() {
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.getBytes())).thenReturn("file1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 2", dirIdRoot.getBytes())).thenReturn("file2");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.getBytes())).thenReturn("dir2");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 3", dirId1.getBytes())).thenReturn("file3");

		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(cloudProvider.read(dir2Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId2.getBytes())));

		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r"))).thenReturn(CompletableFuture.completedFuture(file1Metadata));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r"))).thenReturn(CompletableFuture.completedFuture(file2Metadata));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file3.c9r"))).thenReturn(CompletableFuture.completedFuture(file3Metadata));
		Mockito.when(cloudProvider.itemMetadata(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r"))).thenReturn(CompletableFuture.completedFuture(file4Metadata));

		var dirId1DirFile = new CloudItemMetadata("dir.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r/dir.c9r"), CloudItemType.FILE);
		var dirId2DirFile = new CloudItemMetadata("dir.c9r", dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r/dir.c9r"), CloudItemType.FILE);
		var rootItemList = new CloudItemList(List.of(dir1Metadata, dirId1DirFile, file1Metadata, file2Metadata, file4Metadata, other1Metadata), Optional.empty());
		var dir1ItemList = new CloudItemList(List.of(dir2Metadata, dirId2DirFile, file3Metadata), Optional.empty());
		var dir2ItemList = new CloudItemList(List.of(), Optional.empty());
		Mockito.when(cloudProvider.listExhaustively(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))).thenReturn(CompletableFuture.completedFuture(rootItemList));
		Mockito.when(cloudProvider.listExhaustively(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"))).thenReturn(CompletableFuture.completedFuture(dir1ItemList));
		Mockito.when(cloudProvider.listExhaustively(dataDir.resolve("22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"))).thenReturn(CompletableFuture.completedFuture(dir2ItemList));

		Mockito.when(cloudProvider.delete(dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))).thenReturn(CompletableFuture.completedFuture(null));
		Mockito.when(cloudProvider.delete(dataDir.resolve("11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"))).thenReturn(CompletableFuture.completedFuture(null));
		Mockito.when(cloudProvider.delete(dataDir.resolve("22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"))).thenReturn(CompletableFuture.completedFuture(null));

		var futureResult = decorator.delete(Path.of("/"));
		Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());
	}

	@Test
	@DisplayName("create(\"/Directory 3/\")")
	public void testCreateFolder() {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │  ├─ ...
		 * ├─ Directory 3
		 * ├─ ...
		 * </code>
		 */

		final var dir3Metadata = new CloudItemMetadata("dir3.c9r", dataDir.resolve("00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r"), CloudItemType.FOLDER);

		final var dirId3 = "dir3-id";
		final var hashFolder3Id = "33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD";
		final var dataDirFolder3 = dataDir.resolve("33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 3", dirIdRoot.getBytes())).thenReturn("dir3");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir3", dirIdRoot.getBytes())).thenReturn("Directory 3");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId3)).thenReturn(hashFolder3Id);

		Mockito.when(cloudProvider.createFolder(dir3Metadata.getPath())).thenReturn(CompletableFuture.completedFuture(dir3Metadata.getPath()));
		Mockito.when(cloudProvider.write(Mockito.eq(dir3Metadata.getPath().resolve("dir.c9r")), Mockito.eq(false), Mockito.any(), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.completedFuture(dir3Metadata));
		Mockito.when(cloudProvider.read(dir3Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId3.getBytes())));
		Mockito.when(cloudProvider.createFolder(dataDirFolder3.getParent())).thenReturn(CompletableFuture.completedFuture(dataDirFolder3.getParent()));
		Mockito.when(cloudProvider.createFolder(dataDirFolder3)).thenReturn(CompletableFuture.completedFuture(dataDirFolder3));

		var futureResult = decorator.createFolder(Path.of("/Directory 3/"));
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals("Directory 3", result.getFileName().toString());
		Assertions.assertEquals(Path.of("/Directory 3"), result);
	}

	@Test
	@DisplayName("write(\"/File 1\", replace=false, text, NO_PROGRESS_AWARE)")
	public void testWriteToFile() {
		var file1Content = "hhhhhTOPSECRET!".getBytes();
		var file1Header = "hhhhh".getBytes();

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.getBytes())).thenReturn("file1");
		Mockito.when(cloudProvider.read(dir1Metadata.getPath().resolve("dir.c9r"), ProgressListener.NO_PROGRESS_AWARE)).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(dirId1.getBytes())));
		Mockito.when(cryptor.fileContentCryptor().cleartextChunkSize()).thenReturn(10);

		var header = Mockito.mock(FileHeader.class);
		Mockito.when(fileHeaderCryptor.create()).thenReturn(header);
		Mockito.when(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap(file1Header));
		//Mockito.when(fileContentCryptor.encryptChunk(Mockito.eq(UTF_8.encode("TOPSECRET!")), Mockito.anyLong(), Mockito.eq(header))).thenReturn(UTF_8.encode("geheim!!"));
		Mockito.when(fileContentCryptor.encryptChunk(Mockito.any(), Mockito.anyLong(), Mockito.eq(header)))
				.thenReturn(UTF_8.encode("geheim!!"));
		/*Mockito.when(cloudProvider.write(file1Metadata.getPath(),false, new ByteArrayInputStream("hhhhhgeheim!!".getBytes()), ProgressListener.NO_PROGRESS_AWARE))
				.thenReturn(CompletableFuture.completedFuture(file1Metadata));*/
		Mockito.when(cloudProvider.write(Mockito.eq(file1Metadata.getPath()), Mockito.eq(false), Mockito.any(), Mockito.eq(ProgressListener.NO_PROGRESS_AWARE)))
				.thenReturn(CompletableFuture.completedFuture(file1Metadata));

		var futureResult = decorator.write(Path.of("/File 1"), false, new ByteArrayInputStream(file1Content), ProgressListener.NO_PROGRESS_AWARE);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofMillis(100), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(file1Metadata, result);
	}

}