package org.cryptomator.cloudaccess;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MetadataCachingProviderDecorator implements CloudProvider {

	final Cache<CloudPath, Optional<CloudItemMetadata>> metadataCache;
	private final CloudProvider delegate;

	public MetadataCachingProviderDecorator(CloudProvider delegate) {
		this(delegate, Duration.ofSeconds(10));
	}

	public MetadataCachingProviderDecorator(CloudProvider delegate, Duration cacheEntryMaxAge) {
		this.delegate = delegate;
		this.metadataCache = CacheBuilder.newBuilder().expireAfterWrite(cacheEntryMaxAge).build();
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		var cachedMetadata = metadataCache.getIfPresent(node);
		if (cachedMetadata != null) {
			return cachedMetadata //
					.map(CompletableFuture::completedFuture) //
					.orElseGet(() -> CompletableFuture.failedFuture(new NotFoundException()));
		} else {
			return delegate.itemMetadata(node) //
					.whenComplete((metadata, exception) -> {
						if (exception == null) {
							assert metadata != null;
							metadataCache.put(node, Optional.of(metadata));
						} else if (exception instanceof NotFoundException) {
							metadataCache.put(node, Optional.empty());
						} else {
							metadataCache.invalidate(node);
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
						cloudItemList.getItems().forEach(metadata -> metadataCache.put(metadata.getPath(), Optional.of(metadata)));
					}
				});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener) //
				.whenComplete((metadata, exception) -> {
					if (exception != null) {
						metadataCache.invalidate(file);
					}
				});
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener) //
				.whenComplete((inputStream, exception) -> {
					if (exception != null) {
						metadataCache.invalidate(file);
					}
				});
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, lastModified, progressListener) //
				.whenComplete((nullReturn, exception) -> {
					if (exception != null) {
						metadataCache.invalidate(file);
					}
				});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder) //
				.whenComplete((metadata, exception) -> {
					metadataCache.invalidate(folder);
				});
	}

	@Override
	public CompletionStage<Void> delete(CloudPath node) {
		return delegate.delete(node) //
				.whenComplete((nullReturn, exception) -> {
					evictIncludingDescendants(node);
				});
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace) //
				.whenComplete((path, exception) -> {
					metadataCache.invalidate(source);
					metadataCache.invalidate(target);
				});
	}

	private void evictIncludingDescendants(CloudPath cleartextPath) {
		for (var path : metadataCache.asMap().keySet()) {
			if (path.startsWith(cleartextPath)) {
				metadataCache.invalidate(path);
			}
		}
	}
}
