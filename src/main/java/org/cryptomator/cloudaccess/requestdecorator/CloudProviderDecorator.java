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

interface CloudProviderDecorator extends CloudProvider {

	CloudProvider delegate();

	@Override
	default CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		return delegate().itemMetadata(node);
	}

	@Override
	default CompletionStage<Quota> quota(CloudPath folder) {
		return delegate().quota(folder);
	}

	@Override
	default CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return delegate().list(folder, pageToken);
	}

	@Override
	default CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate().read(file, offset, count, progressListener);
	}

	@Override
	default CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate().write(file, replace, data, size, lastModified, progressListener);
	}

	@Override
	default CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate().createFolder(folder);
	}

	@Override
	default CompletionStage<Void> deleteFile(CloudPath file) {
		return delegate().deleteFile(file);
	}

	@Override
	default CompletionStage<Void> deleteFolder(CloudPath folder) {
		return delegate().deleteFolder(folder);
	}

	@Override
	default CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate().move(source, target, replace);
	}

	@Override
	default boolean cachingCapability() {
		return delegate().cachingCapability();
	}

	@Override
	default CompletionStage<Void> pollRemoteChanges() {
		return delegate().pollRemoteChanges();
	}
}
