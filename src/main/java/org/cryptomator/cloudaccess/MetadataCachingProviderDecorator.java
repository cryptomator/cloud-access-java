package org.cryptomator.cloudaccess;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class MetadataCachingProviderDecorator implements CloudProvider {

	private final static int DEFAULT_CACHE_TIMEOUT_SECONDS = 10;

	// visible for testing
	final Cache<CloudPath, CompletionStage<CloudItemMetadata>> cachedItemMetadataRequests;
	final Cache<ItemListEntry, CompletionStage<CloudItemList>> cachedItemListRequests;

	private final Cache<CloudPath, CompletionStage<Quota>> quotaCache;

	private final CloudProvider delegate;

	public MetadataCachingProviderDecorator(CloudProvider delegate) {
		this(
				delegate, //
				Duration.ofSeconds(Integer.getInteger("org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds", DEFAULT_CACHE_TIMEOUT_SECONDS))
		);
	}

	public MetadataCachingProviderDecorator(CloudProvider delegate, Duration cacheEntryMaxAge) {
		// cachedItemMetadataRequests is a request aggregator only if the delegate has caching capabilities, otherwise it is a real cache
		// cachedItemListRequests is always a request aggregator, as it is too easy to have stale state
		this(
				delegate, //
				cacheEntryMaxAge, //
				delegate.cachingCapability() ? CacheBuilder.newBuilder().build() : CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build(),
				CacheBuilder.newBuilder().build()
		);
	}

	MetadataCachingProviderDecorator(CloudProvider delegate, Duration cacheEntryMaxAge, Cache<CloudPath, CompletionStage<CloudItemMetadata>> cachedItemMetadataRequests, Cache<ItemListEntry, CompletionStage<CloudItemList>> cachedItemListRequests) {
		this.delegate = delegate;
		this.quotaCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
		this.cachedItemMetadataRequests = cachedItemMetadataRequests;
		this.cachedItemListRequests = cachedItemListRequests;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		try {
			return cachedItemMetadataRequests.get(node, () -> delegate.itemMetadata(node).whenComplete((metadata, throwable) -> {
				if (delegate.cachingCapability() || throwable != null && !(throwable instanceof NotFoundException)) {
					evict(node);
				}
			}));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		try {
			return quotaCache.get(folder, () -> delegate.quota(folder).whenComplete((metadata, throwable) -> {
				if (throwable != null && !(throwable instanceof NotFoundException) && !(throwable instanceof QuotaNotAvailableException)) {
					evict(folder);
					quotaCache.invalidate(folder);
				}
			}));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		try {
			var entry = new ItemListEntry(folder, pageToken);
			return cachedItemListRequests.get(entry, () -> delegate.list(folder, pageToken).whenComplete((cloudItemList, exception) -> {
				if (exception instanceof NotFoundException) {
					evictIncludingDescendants(folder);
				} else if (delegate.cachingCapability()) {
					evict(entry);
				} else if (exception == null) {
					evictIncludingDescendants(folder);
					assert cloudItemList != null;
					cloudItemList.getItems().forEach(metadata -> cachedItemMetadataRequests.put(metadata.getPath(), CompletableFuture.completedFuture(metadata)));
				}
			}));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener).whenComplete((metadata, exception) -> {
			if (exception != null) {
				evict(file);
			}
		});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener).whenComplete((inputStream, exception) -> {
			if (exception != null) {
				evict(file);
			}
		});
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, lastModified, progressListener).whenComplete((nullReturn, exception) -> {
			if (exception != null) {
				evict(file);
			} else if (!delegate.cachingCapability()) {
				cachedItemMetadataRequests.put(file, CompletableFuture.completedFuture(new CloudItemMetadata(file.getFileName().toString(), file, CloudItemType.FILE, lastModified, Optional.of(size))));
			}
		});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder).whenComplete((metadata, exception) -> evict(folder));
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		return delegate.deleteFile(file).whenComplete((nullReturn, exception) -> {
			if (!delegate.cachingCapability()) {
				CompletionStage<CloudItemMetadata> future = CompletableFuture.failedFuture(new NotFoundException());
				cachedItemMetadataRequests.put(file, future);
			}
		});
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		return delegate.deleteFolder(folder).whenComplete((nullReturn, exception) -> {
			if (!delegate.cachingCapability()) {
				evictIncludingDescendants(folder);
				CompletionStage<CloudItemMetadata> future = CompletableFuture.failedFuture(new NotFoundException());
				cachedItemMetadataRequests.put(folder, future);
				quotaCache.invalidateAll();
			}
		});
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace).whenComplete((path, exception) -> {
			evictIncludingDescendants(source);
			evictIncludingDescendants(target);
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

	private void evictIncludingDescendants(CloudPath cleartextPath) {
		for (var path : cachedItemMetadataRequests.asMap().keySet()) {
			if (path.startsWith(cleartextPath)) {
				cachedItemMetadataRequests.invalidate(path);
			}
		}
		for (var entry : cachedItemListRequests.asMap().keySet()) {
			if (entry.path.startsWith(cleartextPath)) {
				cachedItemListRequests.invalidate(entry);
			}
		}
	}

	private void evict(CloudPath cleartextPath) {
		cachedItemMetadataRequests.invalidate(cleartextPath);

		for (var entry : cachedItemListRequests.asMap().keySet()) {
			if (entry.path.equals(cleartextPath)) {
				cachedItemListRequests.invalidate(entry);
			}
		}
	}

	private void evict(ItemListEntry entry) {
		cachedItemListRequests.invalidate(entry);
	}

	record ItemListEntry(CloudPath path, Optional<String> pageToken) {
	}
}
