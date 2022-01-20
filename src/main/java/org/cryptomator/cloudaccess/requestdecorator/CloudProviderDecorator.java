package org.cryptomator.cloudaccess.requestdecorator;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.Quota;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

abstract class CloudProviderDecorator implements CloudProvider {

	private final CloudProvider delegate;

	public CloudProviderDecorator(CloudProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		return delegate.itemMetadata(node);
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		return delegate.quota(folder);
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return delegate.list(folder, pageToken);
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener);
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, lastModified, progressListener);
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder);
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		return delegate.deleteFile(file);
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		return delegate.deleteFolder(folder);
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace);
	}

	@Override
	public boolean cachingCapability() {
		return delegate.cachingCapability();
	}

	@Override
	public CompletionStage<Void> pollRemoteChanges() {
		return delegate.pollRemoteChanges();
	}
}
