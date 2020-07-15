package org.cryptomator.cloudaccess.vaultformat8;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cryptolib.api.Cryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class VaultFormat8ProviderDecorator implements CloudProvider {

	private static final Logger LOG = LoggerFactory.getLogger(VaultFormat8ProviderDecorator.class);

	private final CloudProvider delegate;
	private final Path pathToVault;
	private final Cryptor cryptor;

	public VaultFormat8ProviderDecorator(CloudProvider delegate, Path pathToVault, Cryptor cryptor) {
		this.delegate = delegate;
		this.pathToVault = pathToVault;
		this.cryptor = cryptor;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
	}

	@Override
	public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("not implemented"));
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

}
