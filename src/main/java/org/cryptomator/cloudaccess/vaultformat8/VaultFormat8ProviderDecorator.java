package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.common.math.LongMath;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.DecryptingReadableByteChannel;
import org.cryptomator.cryptolib.EncryptingReadableByteChannel;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
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
	private final CloudPath dataDir;
	private final Cryptor cryptor;
	private final DirectoryIdCache dirIdCache;
	private final FileHeaderCache fileHeaderCache;

	public VaultFormat8ProviderDecorator(CloudProvider delegate, CloudPath dataDir, Cryptor cryptor) {
		this.delegate = delegate;
		this.dataDir = dataDir;
		this.cryptor = cryptor;
		this.dirIdCache = new DirectoryIdCache();
		this.fileHeaderCache = new FileHeaderCache();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
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
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		var ciphertextItemList = getDirPathFromClearTextDir(folder).thenCompose(ciphertextPath -> delegate.list(ciphertextPath, pageToken));
		return getDirId(folder).thenCombine(ciphertextItemList, (dirId, itemList) -> toCleartextItemList(itemList, folder, dirId));
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		Preconditions.checkArgument(offset >= 0, "offset must not be negative");
		Preconditions.checkArgument(count >= 0, "count must not be negative");

		// byte range math:
		long firstChunk = offset / cryptor.fileContentCryptor().cleartextChunkSize(); // int-truncate!
		int headerSize = cryptor.fileHeaderCryptor().headerSize();
		long firstByte = headerSize + firstChunk * cryptor.fileContentCryptor().ciphertextChunkSize();
		long lastByte = checkedAdd(offset, count, Long.MAX_VALUE);
		long lastChunk = lastByte / cryptor.fileContentCryptor().cleartextChunkSize(); // int-truncate!
		long numChunks = lastChunk - firstChunk + 1;
		long numBytes = checkedMultiply(numChunks, cryptor.fileContentCryptor().ciphertextChunkSize(), Long.MAX_VALUE);

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
		return futureCleartextStream.thenApply(in -> {
			long skip = offset % cryptor.fileContentCryptor().cleartextChunkSize();
			var offsetIn = new OffsetInputStream(in, skip);
			var limitedIn = ByteStreams.limit(offsetIn, count);
			return limitedIn;
		});
	}

	private long checkedMultiply(long a, long b, long onOverflow) {
		try {
			return LongMath.checkedMultiply(a, b);
		} catch (ArithmeticException e) {
			return onOverflow;
		}
	}

	private long checkedAdd(long a, long b, long onOverflow) {
		try {
			return LongMath.checkedAdd(a, b);
		} catch (ArithmeticException e) {
			return onOverflow;
		}
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(CloudPath file, boolean replace, InputStream data, ProgressListener progressListener) {
		return getC9rPath(file).thenCompose(ciphertextPath -> {
			fileHeaderCache.evict(ciphertextPath);
			try (var src = Channels.newChannel(data); //
				 var encryptingChannel = new EncryptingReadableByteChannel(src, cryptor); //
				 var encryptedIn = Channels.newInputStream(encryptingChannel)) {
				return delegate.write(ciphertextPath, replace, encryptedIn, progressListener);
			} catch (IOException e) {
				return CompletableFuture.failedFuture(e);
			}
		}).thenApply(ciphertextMetadata -> toCleartextMetadata(ciphertextMetadata, file.getParent(), file.getFileName().toString()));
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		final var dirId = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
		final var dirPath = getDirPathWithId(dirId);

		var futureC9rFile = getC9rPath(folder)
				.thenCompose(delegate::createFolder)
				.thenCompose(folderPath -> delegate.write(folderPath.resolve(DIR_FILE_NAME), false, new ByteArrayInputStream(dirId), ProgressListener.NO_PROGRESS_AWARE));

		var futureDir = delegate.createFolderIfNonExisting(dirPath.getParent())
				.thenCompose(unused -> delegate.createFolder(dirPath));

		return futureC9rFile.thenCombine(futureDir, (c9rFile, dir) -> folder);
	}

	@Override
	public CompletionStage<Void> delete(CloudPath node) {
		return itemMetadata(node).thenCompose(cloudNode -> {
			if (cloudNode.getItemType() == CloudItemType.FILE) {
				return getC9rPath(node).thenCompose(ciphertextPath -> {
					fileHeaderCache.evict(ciphertextPath);
					return delegate.delete(ciphertextPath);
				});
			} else {
				return deleteCiphertextDir(getDirPathFromClearTextDir(node)) //
						.thenCompose(ignored -> getC9rPath(node)) //
						.thenCompose(delegate::delete) //
						.thenRun(() -> dirIdCache.evictIncludingDescendants(node));
			}
		});
	}

	private CompletionStage<Void> deleteCiphertextDir(CompletionStage<CloudPath> dirPath) {
		return dirPath //
				.thenCompose(delegate::listExhaustively) //
				.thenApply(itemsList -> itemsList.getItems().stream().filter(subdir -> subdir.getItemType() == CloudItemType.FOLDER).map(CloudItemMetadata::getPath)) //
				.thenApply(subDirsC9rPath -> subDirsC9rPath.map(this::getDirPathFromC9rDir)) //
				.thenApply(subDirsDirPath -> subDirsDirPath.map(this::deleteCiphertextDir)) //
				.thenCompose(result -> {
					var futures = result.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
					return CompletableFuture.allOf(futures);
				}).thenCombine(dirPath, (unused, path) -> path) //
				.thenCompose(delegate::delete);
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return getC9rPath(source).thenCompose(sourceC9rPath -> {
			fileHeaderCache.evict(sourceC9rPath);
			return getC9rPath(target).thenCompose(targetC9rPath -> delegate.move(sourceC9rPath, targetC9rPath, replace));
		}).thenApply(unused -> {
			dirIdCache.evict(source);
			return target;
		});
	}

	private CloudItemList toCleartextItemList(CloudItemList ciphertextItemList, CloudPath cleartextParent, byte[] parentDirId) {
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

	private CloudItemMetadata toCleartextMetadata(CloudItemMetadata ciphertextMetadata, CloudPath cleartextParent, byte[] parentDirId) throws AuthenticationFailedException, IllegalArgumentException {
		var ciphertextName = ciphertextMetadata.getName();
		Preconditions.checkArgument(ciphertextName.endsWith(CIPHERTEXT_FILE_SUFFIX), "Unrecognized file type");
		var ciphertextBaseName = ciphertextName.substring(0, ciphertextName.length() - CIPHERTEXT_FILE_SUFFIX.length());
		var cleartextName = cryptor.fileNameCryptor().decryptFilename(BaseEncoding.base64Url(), ciphertextBaseName, parentDirId);
		return toCleartextMetadata(ciphertextMetadata, cleartextParent, cleartextName);
	}

	private CloudItemMetadata toCleartextMetadata(CloudItemMetadata ciphertextMetadata, CloudPath cleartextParent, String cleartextName) {
		var cleartextPath = cleartextParent.resolve(cleartextName);
		var cleartextSize = ciphertextMetadata.getSize().map(n -> {
			switch (ciphertextMetadata.getItemType()) {
				case FILE:
					return Cryptors.cleartextSize(n - cryptor.fileHeaderCryptor().headerSize(), cryptor);
				case FOLDER:
					return 0L;
				case UNKNOWN: // Fall through
				default:
					throw new IllegalStateException("Unable to retrieve cleartextSize cause of unkown type");
			}
		});
		return new CloudItemMetadata(cleartextName, cleartextPath, ciphertextMetadata.getItemType(), ciphertextMetadata.getLastModifiedDate(), cleartextSize);
	}

	private CompletionStage<byte[]> getDirId(CloudPath cleartextDir) {
		Preconditions.checkNotNull(cleartextDir);
		return dirIdCache.get(cleartextDir, (cleartextPath, parentDirId) -> {
			var cleartextName = cleartextPath.getFileName().toString();
			var ciphertextPath = getC9rPath(parentDirId, cleartextName);
			var dirFileUrl = ciphertextPath.resolve(DIR_FILE_NAME);
			return delegate.read(dirFileUrl, ProgressListener.NO_PROGRESS_AWARE).thenCompose(this::readAllBytes);
		});
	}

	private CompletionStage<FileHeader> readFileHeader(CloudPath ciphertextPath) {
		var headerCryptor = cryptor.fileHeaderCryptor();
		return delegate.read(ciphertextPath, 0, headerCryptor.headerSize(), ProgressListener.NO_PROGRESS_AWARE) //
				.thenCompose(this::readAllBytes) //
				.thenApply(bytes -> headerCryptor.decryptHeader(ByteBuffer.wrap(bytes)));
	}

	private CompletionStage<byte[]> readAllBytes(InputStream inputStream) {
		try (var in = inputStream) {
			return CompletableFuture.completedFuture(in.readAllBytes());
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	private CompletionStage<CloudPath> getDirPathFromClearTextDir(CloudPath cleartextDir) {
		return getDirId(cleartextDir).thenApply(this::getDirPathWithId);
	}

	private CompletionStage<CloudPath> getDirPathFromC9rDir(CloudPath dirC9rPath) {
		return delegate.read(dirC9rPath.resolve(DIR_FILE_NAME), ProgressListener.NO_PROGRESS_AWARE) //
				.thenCompose(this::readAllBytes) //
				.thenApply(this::getDirPathWithId);
	}

	private CloudPath getDirPathWithId(byte[] dirId) {
		var digest = cryptor.fileNameCryptor().hashDirectoryId(new String(dirId, StandardCharsets.UTF_8));
		return dataDir.resolve(digest.substring(0, 2)).resolve(digest.substring(2));
	}

	private CloudPath getC9rPath(byte[] parentDirId, String cleartextName) {
		var ciphertextBaseName = cryptor.fileNameCryptor().encryptFilename(BaseEncoding.base64Url(), cleartextName, parentDirId);
		var ciphertextName = ciphertextBaseName + CIPHERTEXT_FILE_SUFFIX;
		return getDirPathWithId(parentDirId).resolve(ciphertextName);
	}

	private CompletionStage<CloudPath> getC9rPath(CloudPath cleartextPath) {
		Preconditions.checkArgument(cleartextPath.getNameCount() > 0, "No c9r path for root.");
		var cleartextParent = cleartextPath.getNameCount() == 1 ? CloudPath.of("") : cleartextPath.getParent();
		var cleartextName = cleartextPath.getFileName().toString();
		return getDirId(cleartextParent).thenApply(parentDirId -> getC9rPath(parentDirId, cleartextName));
	}

}
