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

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MetadataCachingProviderDecorator implements CloudProvider {

	private final static int DEFAULT_CACHE_TIMEOUT_SECONDS = 10;

	final Cache<CloudPath, Optional<CloudItemMetadata>> itemMetadataCache;
	final Cache<CloudPath, Optional<Quota>> quotaCache;
	private final CloudProvider delegate;

	public MetadataCachingProviderDecorator(CloudProvider delegate) {
		this(delegate, Duration.ofSeconds( //
				Integer.getInteger("org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds", DEFAULT_CACHE_TIMEOUT_SECONDS)
		));
	}

	public MetadataCachingProviderDecorator(CloudProvider delegate, Duration cacheEntryMaxAge) {
		this.delegate = delegate;
		this.itemMetadataCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
		this.quotaCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		var cachedMetadata = itemMetadataCache.getIfPresent(node);
		if (cachedMetadata != null) {
			return cachedMetadata //
					.map(CompletableFuture::completedFuture) //
					.orElseGet(() -> CompletableFuture.failedFuture(new NotFoundException()));
		} else {
			return delegate.itemMetadata(node) //
					.whenComplete((metadata, exception) -> {
						if (exception == null) {
							assert metadata != null;
							itemMetadataCache.put(node, Optional.of(metadata));
						} else if (exception instanceof NotFoundException) {
							itemMetadataCache.put(node, Optional.empty());
						} else {
							itemMetadataCache.invalidate(node);
						}
					});
		}
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		var cachedMetadata = quotaCache.getIfPresent(folder);
		if (cachedMetadata != null) {
			return cachedMetadata //
					.map(CompletableFuture::completedFuture) //
					.orElseGet(() -> CompletableFuture.failedFuture(new NotFoundException()));
		} else {
			return delegate.quota(folder) //
					.whenComplete((quota, exception) -> {
						if (exception == null) {
							assert quota != null;
							quotaCache.put(folder, Optional.of(quota));
						} else if (exception instanceof NotFoundException) {
							quotaCache.put(folder, Optional.empty());
						} else {
							quotaCache.invalidate(folder);
						}
					});
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return delegate.list(folder, pageToken) //
				.whenComplete((cloudItemList, exception) -> {
					evictIncludingDescendants(folder);
					if (exception == null) {
						assert cloudItemList != null;
						cloudItemList.getItems().forEach(metadata -> itemMetadataCache.put(metadata.getPath(), Optional.of(metadata)));
					}
				});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener) //
				.whenComplete((metadata, exception) -> {
					if (exception != null) {
						itemMetadataCache.invalidate(file);
					}
				});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener) //
				.whenComplete((inputStream, exception) -> {
					if (exception != null) {
						itemMetadataCache.invalidate(file);
					}
				});
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, lastModified, progressListener) //
				.whenComplete((nullReturn, exception) -> {
					if (exception != null) {
						itemMetadataCache.invalidate(file);
						quotaCache.invalidateAll();
					}
				});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder) //
				.whenComplete((metadata, exception) -> {
					itemMetadataCache.invalidate(folder);
				});
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		return delegate.deleteFile(file) //
				.whenComplete((nullReturn, exception) -> {
					itemMetadataCache.invalidate(file);
					quotaCache.invalidateAll();
				});
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		return delegate.deleteFolder(folder) //
				.whenComplete((nullReturn, exception) -> {
					evictIncludingDescendants(folder);
					quotaCache.invalidateAll();
				});
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace) //
				.whenComplete((path, exception) -> {
					itemMetadataCache.invalidate(source);
					itemMetadataCache.invalidate(target);
					quotaCache.invalidateAll();
				});
	}

	private void evictIncludingDescendants(CloudPath cleartextPath) {
		for (var path : itemMetadataCache.asMap().keySet()) {
			if (path.startsWith(cleartextPath)) {
				itemMetadataCache.invalidate(path);
			}
		}
	}
}
