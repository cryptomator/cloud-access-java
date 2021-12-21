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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class MetadataRequestAggregator implements CloudProvider {

	private final static int DEFAULT_CACHE_TIMEOUT_SECONDS = 10;

	final Cache<CloudPath, CompletionStage<CloudItemMetadata>> cachedItemMetadataRequests;
	final Cache<Map.Entry<CloudPath, Optional<String>>, CompletionStage<CloudItemList>> cachedItemListRequests;

	final Cache<CloudPath, CompletionStage<Quota>> quotaCache;

	private final CloudProvider delegate;

	public MetadataRequestAggregator(CloudProvider delegate) {
		this(delegate, Duration.ofSeconds( //
				Integer.getInteger("org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds", DEFAULT_CACHE_TIMEOUT_SECONDS) //
		));
	}

	public MetadataRequestAggregator(CloudProvider delegate, Duration cacheEntryMaxAge) {
		this.delegate = delegate;
		this.itemMetadataRequestAggregator = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofSeconds(1)).build();
		this.cloudItemListRequestAggregator = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofSeconds(1)).build();
		this.quotaCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		try {
			return itemMetadataRequestAggregator.get(node, () -> delegate.itemMetadata(node).whenComplete((metadata, throwable) -> {
				// immediately invalidate cache in case of exceptions (except for NOT FOUND):
				if (throwable != null && !(throwable instanceof NotFoundException)) {
					invalidateAggregators(node);
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
				// immediately invalidate cache in case of exceptions (except for NOT FOUND and QUOTA NOT AVAILABLE):
				if (throwable != null && !(throwable instanceof NotFoundException) && !(throwable instanceof QuotaNotAvailableException)) {
					invalidateAggregators(folder);
				}
			}));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		try {
			return cloudItemListRequestAggregator.get(Map.entry(folder, pageToken), () -> delegate.list(folder, pageToken).whenComplete((metadata, throwable) -> {
				// immediately invalidate cache in case of exceptions (except for NOT FOUND):
				if (throwable != null && !(throwable instanceof NotFoundException)) {
					invalidateAggregators(folder);
				}
			}));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener) //
				.whenComplete((metadata, exception) -> {
					if (exception != null) {
						invalidateAggregators(file);
					}
				});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener) //
				.whenComplete((inputStream, exception) -> {
					if (exception != null) {
						invalidateAggregators(file);
					}
				});
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, lastModified, progressListener).whenComplete((nullReturn, exception) -> {
			if (exception != null) {
				invalidateAggregators(file);
			}
		});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder).whenComplete((metadata, exception) -> invalidateAggregators(folder));
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

	private void evictIncludingDescendants(CloudPath cleartextPath) {
		for (var path : itemMetadataRequestAggregator.asMap().keySet()) {
			if (path.startsWith(cleartextPath)) {
				itemMetadataRequestAggregator.invalidate(path);
			}
		}
		for (var path : cloudItemListRequestAggregator.asMap().keySet()) {
			if (path.getKey().startsWith(cleartextPath)) {
				cloudItemListRequestAggregator.invalidate(path);
			}
		}
	}

	private void invalidateAggregators(CloudPath cleartextPath) {
		itemMetadataRequestAggregator.invalidate(cleartextPath);

		for (var path : cloudItemListRequestAggregator.asMap().keySet()) {
			if (path.getKey().equals(cleartextPath)) {
				cloudItemListRequestAggregator.invalidate(path);
			}
		}
	}
}
