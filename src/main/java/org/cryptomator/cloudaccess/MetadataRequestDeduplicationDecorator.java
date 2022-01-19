package org.cryptomator.cloudaccess;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.Quota;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.QuotaNotAvailableException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class MetadataRequestDeduplicationDecorator implements CloudProvider {

	// visible for testing
	final AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests;
	final AsyncCache<ItemListEntry, CloudItemList> cachedItemListRequests;
	final AsyncCache<CloudPath, Quota> quotaCache;

	private final CloudProvider delegate;

	public MetadataRequestDeduplicationDecorator(CloudProvider delegate) {
		this(
				delegate, //
				Caffeine.newBuilder().buildAsync(), //
				Caffeine.newBuilder().buildAsync(), //
				Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(10)).buildAsync()
		);
	}

	MetadataRequestDeduplicationDecorator(
			CloudProvider delegate, //
			AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests, //
			AsyncCache<ItemListEntry, CloudItemList> cachedItemListRequests, //
			AsyncCache<CloudPath, Quota> quotaCache
	) {
		this.delegate = delegate;
		this.quotaCache = quotaCache;
		this.cachedItemMetadataRequests = cachedItemMetadataRequests;
		this.cachedItemListRequests = cachedItemListRequests;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		return cachedItemMetadataRequests.get(node, k -> delegate.itemMetadata(k)
				.whenComplete((metadata, throwable) -> cachedItemMetadataRequests.synchronous().invalidate(node))
				.toCompletableFuture().join());
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		return quotaCache.get(folder, k -> delegate.quota(k)
				.whenComplete((metadata, throwable) -> {
					if (throwable != null && !(throwable instanceof NotFoundException) && !(throwable instanceof QuotaNotAvailableException)) {
						quotaCache.synchronous().invalidate(folder);
					}
				}).toCompletableFuture().join());
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		var entry = new ItemListEntry(folder, pageToken);
		return cachedItemListRequests.get(entry, k -> delegate.list(k.path, k.pageToken)
				.whenComplete((cloudItemList, exception) -> cachedItemListRequests.synchronous().invalidate(entry))
				.toCompletableFuture().join());
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener);
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
		return delegate.move(source, target, replace).whenComplete((path, exception) -> {
			evictFromItemAndItemListCacheIncludingDescendants(source);
			evictFromItemAndItemListCacheIncludingDescendants(target);
		});
	}

	@Override
	public boolean cachingCapability() {
		return delegate.cachingCapability();
	}

	@Override
	public CompletionStage<Void> pollRemoteChanges() {
		return delegate.pollRemoteChanges();
	}

	private synchronized void evictFromItemAndItemListCacheIncludingDescendants(CloudPath cleartextPath) {
		cachedItemMetadataRequests
				.synchronous()
				.invalidateAll(cachedItemMetadataRequests.asMap().keySet().stream().filter(path -> path.startsWith(cleartextPath)).collect(Collectors.toSet()));

		cachedItemListRequests
				.synchronous()
				.invalidateAll(cachedItemListRequests.asMap().keySet().stream().filter(entry -> entry.path.startsWith(cleartextPath)).collect(Collectors.toSet()));
	}

	record ItemListEntry(CloudPath path, Optional<String> pageToken) {
	}
}
