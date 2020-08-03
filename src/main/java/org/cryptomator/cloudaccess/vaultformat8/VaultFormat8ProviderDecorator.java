package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.DecryptingReadableByteChannel;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
		var ciphertextItemList = getDirPath(folder).thenCompose(ciphertextPath -> delegate.list(ciphertextPath, pageToken));
		return getDirId(folder).thenCombine(ciphertextItemList, (dirId, itemList) -> toCleartextItemList(itemList, folder, dirId));
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
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
	public CompletionStage<CloudItemMetadata> write(CloudPath file, boolean replace, InputStream data, ProgressListener progressListener) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<Void> delete(CloudPath node) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	/* support */

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
		var cleartextPath = cleartextParent.resolve(cleartextName);
		var cleartextSize = ciphertextMetadata.getSize().map(n -> Cryptors.cleartextSize(n, cryptor));
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

	private CompletionStage<CloudPath> getDirPath(CloudPath cleartextDir) {
		return getDirId(cleartextDir).thenApply(this::getDirPath);
	}

	private CloudPath getDirPath(byte[] dirId) {
		var digest = cryptor.fileNameCryptor().hashDirectoryId(new String(dirId, StandardCharsets.UTF_8));
		return dataDir.resolve(digest.substring(0, 2)).resolve(digest.substring(2));
	}

	private CloudPath getC9rPath(byte[] parentDirId, String cleartextName) {
		var ciphertextBaseName = cryptor.fileNameCryptor().encryptFilename(BaseEncoding.base64Url(), cleartextName, parentDirId);
		var ciphertextName = ciphertextBaseName + CIPHERTEXT_FILE_SUFFIX;
		return getDirPath(parentDirId).resolve(ciphertextName);
	}

	private CompletionStage<CloudPath> getC9rPath(CloudPath cleartextPath) {
		Preconditions.checkArgument(cleartextPath.getNameCount() > 0, "No c9r path for root.");
		var cleartextParent = cleartextPath.getNameCount() == 1 ? CloudPath.of("") : cleartextPath.getParent();
		var cleartextName = cleartextPath.getFileName().toString();
		return getDirId(cleartextParent).thenApply(parentDirId -> getC9rPath(parentDirId, cleartextName));
	}

}
