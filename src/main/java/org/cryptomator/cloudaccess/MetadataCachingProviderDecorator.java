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
	private final Cache<CloudPath, Optional<CloudItemMetadata>> metadataCache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofSeconds(10)).build();

	public MetadataCachingProviderDecorator(CloudProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		var cachedMetadata = metadataCache.getIfPresent(node);
		if (cachedMetadata != null) {
			return cachedMetadata
					.map(CompletableFuture::completedFuture)
					.orElseGet(() -> CompletableFuture.failedFuture(new NotFoundException()));
		} else {
			return delegate.itemMetadata(node).thenApply(metadata -> {
				metadataCache.put(node, Optional.of(metadata));
				return metadata;
			}).handle((metadata, exception) -> {
				if (exception == null) {
					assert metadata != null;
					return CompletableFuture.completedFuture(metadata);
				} else if (exception instanceof NotFoundException) {
					metadataCache.put(node, Optional.empty());
					return CompletableFuture.<CloudItemMetadata>failedFuture(exception);
				} else {
					return CompletableFuture.<CloudItemMetadata>failedFuture(exception);
				}
			}).thenCompose(Function.identity());
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return delegate.list(folder, pageToken).thenApply(cloudItemList -> {
			cloudItemList.getItems().forEach(metadata -> metadataCache.put(metadata.getPath(), Optional.of(metadata)));
			return cloudItemList;
		});
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
	public CompletionStage<CloudItemMetadata> write(CloudPath file, boolean replace, InputStream data, long size, ProgressListener progressListener) {
		return delegate.write(file, replace, data, size, progressListener).thenApply(metadata -> {
			metadataCache.put(file, Optional.of(metadata));
			return metadata;
		});
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return delegate.createFolder(folder).thenApply(path -> {
			metadataCache.invalidate(path);
			return path;
		});
	}

	@Override
	public CompletionStage<Void> delete(CloudPath node) {
		return delegate.delete(node).thenApply(unused -> {
			metadataCache.invalidate(node);
			return null;
		});
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return delegate.move(source, target, replace).thenApply(path -> {
			metadataCache.invalidate(source);
			metadataCache.invalidate(target);
			return path;
		});
	}
}
