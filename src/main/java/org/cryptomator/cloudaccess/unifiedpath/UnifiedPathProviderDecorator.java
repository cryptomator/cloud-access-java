package org.cryptomator.cloudaccess.unifiedpath;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * TODO: return, if necessary, a system dependet path again!
 * Path --> CloudPath --> do shiat --> CloudPath --> Path
 */
public class UnifiedPathProviderDecorator implements CloudProvider {


	private final CloudProvider delegate;

	public UnifiedPathProviderDecorator(CloudProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
		return delegate.itemMetadata(CloudPath.of(delegate, node));
	}

	@Override
	public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
		return delegate.list(CloudPath.of(delegate, folder), pageToken);
	}

	@Override
	public CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(CloudPath.of(delegate, file), offset, count, progressListener);
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
		return delegate.write(CloudPath.of(delegate, file), replace, data, progressListener);
	}

	@Override
	public CompletionStage<Path> createFolder(Path folder) {
		return delegate.createFolder(CloudPath.of(delegate, folder)).thenApply(path -> folder.getFileSystem().getPath(path.toString()));
	}

	@Override
	public CompletionStage<Void> delete(Path node) {
		return delegate.delete(CloudPath.of(delegate, node));
	}

	@Override
	public CompletionStage<Path> move(Path source, Path target, boolean replace) {
		return delegate.move(CloudPath.of(delegate, source), CloudPath.of(delegate, target), replace).thenApply(path -> target.getFileSystem().getPath(path.toString()));
	}
}
