package org.cryptomator.cloudaccess;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class MetadataCachingProviderDecorator implements CloudProvider {

	private final static int DEFAULT_CACHE_TIMEOUT_SECONDS = 10;

	private final Cache<CloudPath, CompletionStage<CloudItemMetadata>> cachedItemMetadataRequests;
	private final Cache<ItemListEntry, CompletionStage<CloudItemList>> cachedItemListRequests;

	private final Cache<CloudPath, CompletionStage<Quota>> quotaCache;

	private final CloudProvider delegate;

	public MetadataCachingProviderDecorator(CloudProvider delegate) {
		this(
				delegate, //
				Duration.ofSeconds(Integer.getInteger("org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds", DEFAULT_CACHE_TIMEOUT_SECONDS))
		);
	}

	public MetadataCachingProviderDecorator(CloudProvider delegate, Duration cacheEntryMaxAge) {
		this.delegate = delegate;
		this.cachedItemMetadataRequests = CacheBuilder.newBuilder().build();
		this.cachedItemListRequests = CacheBuilder.newBuilder().build();
		this.quotaCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		try {
			return cachedItemMetadataRequests.get(node, () -> delegate.itemMetadata(node).whenComplete((metadata, throwable) -> evict(node)));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		try {
			return quotaCache.get(folder, () -> delegate.quota(folder).whenComplete((metadata, throwable) -> {
				// immediately invalidate cache in case of exceptions (except for NOT FOUND and QUOTA NOT AVAILABLE):
				if (throwable != null && !(throwable instanceof NotFoundException) && !(throwable instanceof QuotaNotAvailableException)) {
					evict(folder);
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
			return cachedItemListRequests.get(entry, () -> delegate.list(folder, pageToken).whenComplete((metadata, throwable) -> evict(entry)));
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
			}
		});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder).whenComplete((metadata, exception) -> evict(folder));
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		return delegate.deleteFile(file);
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		return delegate.deleteFolder(folder).whenComplete((nullReturn, exception) -> evictIncludingDescendants(folder));
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

	private record ItemListEntry(CloudPath path, Optional<String> pageToken) {
	}
}
