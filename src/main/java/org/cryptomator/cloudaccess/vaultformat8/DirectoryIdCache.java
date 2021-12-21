package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cloudaccess.api.CloudPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

class DirectoryIdCache {

	private static final byte[] ROOT_DIR_ID = new byte[0];
	private static final Map<CloudPath, CompletionStage<byte[]>> ROOT_MAPPINGS = Map.of(CloudPath.of(""), CompletableFuture.completedFuture(ROOT_DIR_ID), CloudPath.of("/"), CompletableFuture.completedFuture(ROOT_DIR_ID));

	private final Cache<CloudPath, CompletionStage<byte[]>> cache;

	public DirectoryIdCache() {
		cache = CacheBuilder.newBuilder().build();
		cache.putAll(ROOT_MAPPINGS);
	}

	public synchronized CompletionStage<byte[]> get(CloudPath cleartextPath, BiFunction<CloudPath, byte[], CompletionStage<byte[]>> onMiss) {
		try {
			return cache.get(cleartextPath, () -> {
				var parentPath = cleartextPath.getNameCount() == 1 ? CloudPath.of("") : cleartextPath.getParent();
				return get(parentPath, onMiss).thenCompose(parentDirId -> onMiss.apply(cleartextPath, parentDirId));
			});
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public synchronized void evict(CloudPath cleartextPath) {
		cache.invalidate(cleartextPath);
	}

	public synchronized void evictIncludingDescendants(CloudPath cleartextPath) {
		for (var path : cache.asMap().keySet()) {
			if (path.startsWith(cleartextPath)) {
				cache.invalidate(path);
			}
		}
	}
}
