package org.cryptomator.cloudaccess.requestdecorator;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Decorates an existing CloudProvider by deduplicating identical itemMetadata and list-requests so that the delegate is called only once until the future is completed.
 */
class MetadataRequestDeduplicationDecorator implements CloudProviderDecorator {

	// visible for testing
	final AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests;
	final AsyncCache<ItemListEntry, CloudItemList> cachedItemListRequests;

	private final CloudProvider delegate;

	public MetadataRequestDeduplicationDecorator(CloudProvider delegate) {
		this(delegate, //
				Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync(), //
				Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync());
	}

	MetadataRequestDeduplicationDecorator(
			CloudProvider delegate, //
			AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests, //
			AsyncCache<ItemListEntry, CloudItemList> cachedItemListRequests) {
		this.delegate = delegate;
		this.cachedItemMetadataRequests = cachedItemMetadataRequests;
		this.cachedItemListRequests = cachedItemListRequests;
	}

	@Override
	public CloudProvider delegate() {
		return delegate;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		return cachedItemMetadataRequests.get(node, (key, executor) -> delegate.itemMetadata(key).toCompletableFuture());
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		var entry = new ItemListEntry(folder, pageToken);
		return cachedItemListRequests.get(entry, (key, executor) -> delegate.list(key.path, key.pageToken).toCompletableFuture());
	}

	record ItemListEntry(CloudPath path, Optional<String> pageToken) {
	}
}
