package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
	private final Path dataDir;
	private final Cryptor cryptor;
	private final DirectoryIdCache dirIdCache;

	public VaultFormat8ProviderDecorator(CloudProvider delegate, Path dataDir, Cryptor cryptor) {
		this.delegate = delegate;
		this.dataDir = dataDir;
		this.cryptor = cryptor;
		this.dirIdCache = new DirectoryIdCache();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
		var futureParentDirId = getDirId(node.getParent());
		var futureCiphertextMetadata = futureParentDirId.thenApply(parentDirId -> getCiphertextPath(node, parentDirId)).thenCompose(delegate::itemMetadata);
		return futureCiphertextMetadata.thenCombine(futureParentDirId, (ciphertextMetadata, parentDirId) -> toCleartextMetadata(ciphertextMetadata, node.getParent(), parentDirId));
	}

	@Override
	public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
		var ciphertextItemList = getDirPath(folder).thenCompose(ciphertextPath -> delegate.list(ciphertextPath, pageToken));
		return getDirId(folder).thenCombine(ciphertextItemList, (dirId, itemList) -> toCleartextItemList(itemList, folder, dirId));
	}

	@Override
	public CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<Path> createFolder(Path folder) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<Void> delete(Path node) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<Path> move(Path source, Path target, boolean replace) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	/* support */

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
			var ciphertextPath = getCiphertextPath(cleartextPath, parentDirId);
			var dirFileUrl = ciphertextPath.resolve(DIR_FILE_NAME);
			return delegate.read(dirFileUrl, ProgressListener.NO_PROGRESS_AWARE).thenCompose(this::readAllBytes);
		});
	}

	private CompletionStage<byte[]> readAllBytes(InputStream inputStream) {
		try (var in = inputStream) {
			return CompletableFuture.completedFuture(in.readAllBytes());
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	private CompletionStage<Path> getDirPath(Path cleartextDir) {
		return getDirId(cleartextDir).thenApply(this::getDirPath);
	}

	private Path getDirPath(byte[] dirId) {
		var digest = cryptor.fileNameCryptor().hashDirectoryId(new String(dirId, StandardCharsets.UTF_8));
		return dataDir.resolve(digest.substring(0, 2)).resolve(digest.substring(2));
	}

	private Path getCiphertextPath(Path cleartextPath, byte[] parentDirId) {
		var ciphertextBaseName = cryptor.fileNameCryptor().encryptFilename(BaseEncoding.base64Url(), cleartextPath.getFileName().toString(), parentDirId);
		var ciphertextName = ciphertextBaseName + CIPHERTEXT_FILE_SUFFIX;
		return getDirPath(parentDirId).resolve(ciphertextName);
	}

//	private CompletionStage<Path> getCiphertextPath(Path cleartextPath) {
//		var cleartextParent = cleartextPath.getNameCount() == 1 ? Path.of("") : cleartextPath.getParent();
//		return getDirId(cleartextParent).thenApply(parentDirId -> getCiphertextPath(cleartextPath, parentDirId));
//	}

}
