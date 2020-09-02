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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class MetadataCachingProviderDecorator implements CloudProvider {

	private final CloudProvider delegate;
	final Cache<CloudPath, Optional<CloudItemMetadata>> metadataCache;

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
			return cachedMetadata
					.map(CompletableFuture::completedFuture)
					.orElseGet(() -> CompletableFuture.failedFuture(new NotFoundException()));
		} else {
			return delegate.itemMetadata(node)
					.handle((metadata, exception) -> {
						if (exception == null) {
							assert metadata != null;
							metadataCache.put(node, Optional.of(metadata));
							return CompletableFuture.completedFuture(metadata);
						} else if (exception instanceof NotFoundException) {
							metadataCache.put(node, Optional.empty());
							return CompletableFuture.<CloudItemMetadata>failedFuture(exception);
						} else {
							metadataCache.invalidate(node);
							return CompletableFuture.<CloudItemMetadata>failedFuture(exception);
						}
					}).thenCompose(Function.identity());
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return delegate.list(folder, pageToken)
				.handle((cloudItemList, exception) -> {
					evictIncludingDescendants(folder);
					if (exception == null) {
						assert cloudItemList != null;
						cloudItemList.getItems().forEach(metadata -> metadataCache.put(metadata.getPath(), Optional.of(metadata)));
						return CompletableFuture.completedFuture(cloudItemList);
					} else {
						return CompletableFuture.<CloudItemList>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return delegate.read(file, progressListener)
				.handle((metadata, exception) -> {
					if (exception == null) {
						assert metadata != null;
						return CompletableFuture.completedFuture(metadata);
					} else {
						metadataCache.invalidate(file);
						return CompletableFuture.<InputStream>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return delegate.read(file, offset, count, progressListener)
				.handle((inputStream, exception) -> {
					if (exception == null) {
						assert inputStream != null;
						return CompletableFuture.completedFuture(inputStream);
					} else {
						metadataCache.invalidate(file);
						return CompletableFuture.<InputStream>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(CloudPath file, boolean replace, InputStream data, long size, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, progressListener).thenApply(metadata -> {
			metadataCache.put(file, Optional.of(metadata));
			return metadata;
		}).handle((metadata, exception) -> {
			if (exception == null) {
				assert metadata != null;
				return CompletableFuture.completedFuture(metadata);
			} else {
				metadataCache.invalidate(file);
				return CompletableFuture.<CloudItemMetadata>failedFuture(exception);
			}
		}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder)
				.handle((metadata, exception) -> {
					metadataCache.invalidate(folder);
					if (exception == null) {
						assert metadata != null;
						return CompletableFuture.completedFuture(metadata);
					} else {
						return CompletableFuture.<CloudPath>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<Void> delete(CloudPath node) {
		return delegate.delete(node)
				.handle((nullReturn, exception) -> {
					evictIncludingDescendants(node);
					if (exception == null) {
						return CompletableFuture.completedFuture(nullReturn);
					} else {
						return CompletableFuture.<Void>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace)
				.handle((path, exception) -> {
					metadataCache.invalidate(source);
					metadataCache.invalidate(target);
					if (exception == null) {
						return CompletableFuture.completedFuture(path);
					} else {
						return CompletableFuture.<CloudPath>failedFuture(exception);
					}
				}).thenCompose(Function.identity());
	}

	private void evictIncludingDescendants(CloudPath cleartextPath) {
		for(var path : metadataCache.asMap().keySet()) {
			if(path.startsWith(cleartextPath)) {
				metadataCache.invalidate(path);
			}
		}
	}
}
