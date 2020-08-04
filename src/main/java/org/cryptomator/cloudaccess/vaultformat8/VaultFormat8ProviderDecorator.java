package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.DecryptingReadableByteChannel;
import org.cryptomator.cryptolib.EncryptingWritableByteChannel;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VaultFormat8ProviderDecorator implements CloudProvider {

	private static final Logger LOG = LoggerFactory.getLogger(VaultFormat8ProviderDecorator.class);
	private static final String CIPHERTEXT_FILE_SUFFIX = ".c9r";
	private static final String DIR_FILE_NAME = "dir.c9r";

	private final CloudProvider delegate;
	private final Path dataDir;
	private final Path tmpDir;
	private final Cryptor cryptor;
	private final DirectoryIdCache dirIdCache;
	private final FileHeaderCache fileHeaderCache;

	public VaultFormat8ProviderDecorator(CloudProvider delegate, Path dataDir, Path tmpDir, Cryptor cryptor) {
		this.delegate = delegate;
		this.dataDir = dataDir;
		this.tmpDir = tmpDir;
		this.cryptor = cryptor;
		this.dirIdCache = new DirectoryIdCache();
		this.fileHeaderCache = new FileHeaderCache();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
		if (node.getNameCount() == 0) {
			// ROOT
			return CompletableFuture.completedFuture(new CloudItemMetadata("", node, CloudItemType.FOLDER, Optional.empty(), Optional.empty()));
		} else {
			var futureParentDirId = getDirId(node.getParent());
			var cleartextName = node.getFileName().toString();
			var futureCiphertextMetadata = futureParentDirId.thenApply(parentDirId -> getC9rPath(parentDirId, cleartextName)).thenCompose(delegate::itemMetadata);
			return futureCiphertextMetadata.thenCombine(futureParentDirId, (ciphertextMetadata, parentDirId) -> toCleartextMetadata(ciphertextMetadata, node.getParent(), parentDirId));
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
		var ciphertextItemList = getDirPathFromClearTextDir(folder).thenCompose(ciphertextPath -> delegate.list(ciphertextPath, pageToken));
		return getDirId(folder).thenCombine(ciphertextItemList, (dirId, itemList) -> toCleartextItemList(itemList, folder, dirId));
	}

	@Override
	public CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener) {
		// byte range math:
		long firstChunk = offset / cryptor.fileContentCryptor().cleartextChunkSize(); // int-truncate!
		long lastChunk = (offset + count) / cryptor.fileContentCryptor().cleartextChunkSize(); // int-truncate!
		int headerSize = cryptor.fileHeaderCryptor().headerSize();
		long firstByte = headerSize + firstChunk * cryptor.fileContentCryptor().ciphertextChunkSize();
		long numBytes = (lastChunk - firstChunk + 1) * cryptor.fileContentCryptor().ciphertextChunkSize();

		// loading of relevant parts from ciphertext file:
		var futureCiphertextPath = getC9rPath(file);
		var futureHeader = futureCiphertextPath.thenCompose(ciphertextPath -> fileHeaderCache.get(ciphertextPath, this::readFileHeader));
		var futureCiphertext = futureCiphertextPath.thenCompose(ciphertextPath -> delegate.read(ciphertextPath, firstByte, numBytes, progressListener));
		var futureCleartextStream = futureHeader.thenCombine(futureCiphertext, (header, ciphertext) -> {
			var ciphertextChannel = Channels.newChannel(ciphertext);
			var cleartextChannel = new DecryptingReadableByteChannel(ciphertextChannel, cryptor, true, header, firstChunk);
			return Channels.newInputStream(cleartextChannel);
		});

		// adjust range:
		long skip = offset % cryptor.fileContentCryptor().cleartextChunkSize();
		assert skip + count < (lastChunk + 1) * cryptor.fileContentCryptor().cleartextChunkSize();
		return futureCleartextStream.thenApply(in -> {
			var offsetIn = new OffsetInputStream(in, skip);
			var limitedIn = ByteStreams.limit(offsetIn, count);
			return limitedIn;
		});
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
		return getC9rPath(file).thenCompose(filePath -> {
			try {
				final var encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", tmpDir.toFile());
				try (var writableByteChannel = Channels.newChannel(new FileOutputStream(encryptedTmpFile));
					 var encryptingWritableByteChannel = new EncryptingWritableByteChannel(writableByteChannel, cryptor)){
					var buff = ByteBuffer.allocate(cryptor.fileContentCryptor().cleartextChunkSize());
					int read;
					while ((read = data.read(buff.array())) > 0) {
						buff.limit(read);
						encryptingWritableByteChannel.write(buff);
						buff.flip();
					}
					encryptingWritableByteChannel.close();
					try(var encryptedTmpFileInputStream = new FileInputStream(encryptedTmpFile)) {
						// TODO handle write conflict (check again replace is false && exists(file) --> append conflict to file name
						return delegate.write(filePath, replace, encryptedTmpFileInputStream, progressListener);
					}
				} catch (Throwable e) {
					return CompletableFuture.failedFuture(e);
				} finally {
					encryptedTmpFile.delete();
				}
			} catch (IOException e) {
				return CompletableFuture.failedFuture(e);
			}
		});
	}

	@Override
	public CompletionStage<Path> createFolder(Path folder) {
		BiFunction<Path, Throwable, CompletableFuture<Path>> handler = (result, exception) -> {
			if (exception == null) {
				return CompletableFuture.completedFuture(result);
			} else if (exception instanceof FileAlreadyExistsException) {
				return CompletableFuture.completedFuture(result);
			} else {
				return CompletableFuture.failedFuture(exception);
			}
		};

		final var dirId = UUID.randomUUID().toString();

		return getC9rPath(folder)
				.thenCompose(delegate::createFolder)
				.thenCompose(folderPath -> delegate.write(folderPath.resolve(DIR_FILE_NAME), false, new ByteArrayInputStream(dirId.getBytes(StandardCharsets.UTF_8)), ProgressListener.NO_PROGRESS_AWARE))
				.thenCompose(unused -> getDirPathFromClearTextDir(folder))
					.thenCompose(dirPath -> delegate.createFolder(dirPath.getParent()).handle(handler)
					.thenCompose(unused -> delegate.createFolder(dirPath)))
				.thenApply(dirPath -> folder);
	}

	@Override
	public CompletionStage<Void> delete(Path node) {
		return itemMetadata(node).thenCompose(cloudNode -> {
			if(cloudNode.getItemType() == CloudItemType.FILE) {
				return getC9rPath(node).thenCompose(delegate::delete);
			} else {
				return deleteCiphertextDir(getDirPathFromClearTextDir(node));
			}
		});
	}

	private CompletionStage<Void> deleteCiphertextDir(CompletionStage<Path> dirPath) {
		// TODO evict dir from cache (but first decrypt)

		return dirPath
				.thenCompose(delegate::listExhaustively)
				.thenApply(itemsList -> itemsList.getItems().stream().filter(subdir -> subdir.getItemType() == CloudItemType.FOLDER).map(CloudItemMetadata::getPath))
				.thenApply(subDirsC9rPath -> subDirsC9rPath.map(this::getDirPathFromC9rDir))
				.thenApply(subDirsDirPath -> subDirsDirPath.map(this::deleteCiphertextDir))
				.thenCompose(result -> {
					var futures = result.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
					return CompletableFuture.allOf(futures);
				}).thenCombine(dirPath, (unused, path) -> path)
				.thenCompose(delegate::delete);
	}

	@Override
	public CompletionStage<Path> move(Path source, Path target, boolean replace) {
		return getC9rPath(source).thenCompose(sourcec9rPath -> getC9rPath(target).thenCompose(targetc9rPath -> delegate.move(sourcec9rPath, targetc9rPath, replace)))
				.thenApply(unused -> {
					dirIdCache.evict(source);
					return target;
				});
	}

	private CloudItemList toCleartextItemList(CloudItemList ciphertextItemList, Path cleartextParent, byte[] parentDirId) {
		var items = ciphertextItemList.getItems().stream().flatMap(ciphertextMetadata -> {
			try {
				var cleartextMetadata = toCleartextMetadata(ciphertextMetadata, cleartextParent, parentDirId);
				return Stream.of(cleartextMetadata);
			} catch (AuthenticationFailedException e) {
				LOG.warn("Unauthentic ciphertext file name: {}", ciphertextMetadata.getPath());
				return Stream.empty();
			} catch (IllegalArgumentException e) {
				LOG.debug("Skipping unknown file: {}", ciphertextMetadata.getPath());
				return Stream.empty();
			}
		}).collect(Collectors.toList());
		return new CloudItemList(items, ciphertextItemList.getNextPageToken());
	}

	private CloudItemMetadata toCleartextMetadata(CloudItemMetadata ciphertextMetadata, Path cleartextParent, byte[] parentDirId) throws AuthenticationFailedException, IllegalArgumentException {
		var ciphertextName = ciphertextMetadata.getName();
		Preconditions.checkArgument(ciphertextName.endsWith(CIPHERTEXT_FILE_SUFFIX), "Unrecognized file type");
		var ciphertextBaseName = ciphertextName.substring(0, ciphertextName.length() - CIPHERTEXT_FILE_SUFFIX.length());
		var cleartextName = cryptor.fileNameCryptor().decryptFilename(BaseEncoding.base64Url(), ciphertextBaseName, parentDirId);
		var cleartextPath = cleartextParent.resolve(cleartextName);
		var cleartextSize = ciphertextMetadata.getSize().map(n -> Cryptors.cleartextSize(n, cryptor));
		return new CloudItemMetadata(cleartextName, cleartextPath, ciphertextMetadata.getItemType(), ciphertextMetadata.getLastModifiedDate(), cleartextSize);
	}

	private CompletionStage<byte[]> getDirId(Path cleartextDir) {
		Preconditions.checkNotNull(cleartextDir);
		return dirIdCache.get(cleartextDir, (cleartextPath, parentDirId) -> {
			var cleartextName = cleartextPath.getFileName().toString();
			var ciphertextPath = getC9rPath(parentDirId, cleartextName);
			var dirFileUrl = ciphertextPath.resolve(DIR_FILE_NAME);
			return delegate.read(dirFileUrl, ProgressListener.NO_PROGRESS_AWARE).thenCompose(this::readAllBytes);
		});
	}

	private CompletionStage<FileHeader> readFileHeader(Path ciphertextPath) {
		var headerCryptor = cryptor.fileHeaderCryptor();
		return delegate.read(ciphertextPath, 0, headerCryptor.headerSize(), ProgressListener.NO_PROGRESS_AWARE)
				.thenCompose(this::readAllBytes)
				.thenApply(bytes -> headerCryptor.decryptHeader(ByteBuffer.wrap(bytes)));
	}

	private CompletionStage<byte[]> readAllBytes(InputStream inputStream) {
		try (var in = inputStream) {
			return CompletableFuture.completedFuture(in.readAllBytes());
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	private CompletionStage<Path> getDirPathFromClearTextDir(Path cleartextDir) {
		return getDirId(cleartextDir).thenApply(this::getDirPathFromClearTextDir);
	}

	private CompletionStage<Path> getDirPathFromC9rDir(Path dirC9rPath) {
		return delegate.read(dirC9rPath.resolve(DIR_FILE_NAME), ProgressListener.NO_PROGRESS_AWARE)
				.thenCompose(this::readAllBytes)
				.thenApply(this::getDirPathFromClearTextDir);
	}

	private Path getDirPathFromClearTextDir(byte[] dirId) {
		var digest = cryptor.fileNameCryptor().hashDirectoryId(new String(dirId, StandardCharsets.UTF_8));
		return dataDir.resolve(digest.substring(0, 2)).resolve(digest.substring(2));
	}

	private Path getC9rPath(byte[] parentDirId, String cleartextName) {
		var ciphertextBaseName = cryptor.fileNameCryptor().encryptFilename(BaseEncoding.base64Url(), cleartextName, parentDirId);
		var ciphertextName = ciphertextBaseName + CIPHERTEXT_FILE_SUFFIX;
		return getDirPathFromClearTextDir(parentDirId).resolve(ciphertextName);
	}

	private CompletionStage<Path> getC9rPath(Path cleartextPath) {
		Preconditions.checkArgument(cleartextPath.getNameCount() > 0, "No c9r path for root.");
		var cleartextParent = cleartextPath.getNameCount() == 1 ? Path.of("") : cleartextPath.getParent();
		var cleartextName = cleartextPath.getFileName().toString();
		return getDirId(cleartextParent).thenApply(parentDirId -> getC9rPath(parentDirId, cleartextName));
	}

}
