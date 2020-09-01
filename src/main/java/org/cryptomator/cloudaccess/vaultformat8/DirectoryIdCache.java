package org.cryptomator.cloudaccess.vaultformat8;

import org.cryptomator.cloudaccess.api.CloudPath;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

class DirectoryIdCache {

	private static final byte[] ROOT_DIR_ID = new byte[0];
	private static final Map<CloudPath, byte[]> ROOT_MAPPINGS = Map.of(CloudPath.of(""), ROOT_DIR_ID, CloudPath.of("/"), ROOT_DIR_ID);

	private final ConcurrentMap<CloudPath, byte[]> cache = new ConcurrentHashMap<>(ROOT_MAPPINGS);

	public CompletionStage<byte[]> get(CloudPath cleartextPath, BiFunction<CloudPath, byte[], CompletionStage<byte[]>> onMiss) {
		var cached = cache.get(cleartextPath);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		} else {
			var parentPath = cleartextPath.getNameCount() == 1 ? CloudPath.of("") : cleartextPath.getParent();
			return get(parentPath, onMiss).thenCompose(parentDirId -> {
				return onMiss.apply(cleartextPath, parentDirId);
			}).thenApply(dirId -> {
				cache.put(cleartextPath, dirId);
				return dirId;
			});
		}
	}

	public void evict(CloudPath cleartextPath) {
		cache.remove(cleartextPath);
	}

	public void evictIncludingDescendants(CloudPath cleartextPath) {
		for(var path : cache.keySet()) {
			if(path.startsWith(cleartextPath)) {
				cache.remove(path);
			}
		}
	}

	Optional<byte[]> getCached(CloudPath cleartextPath) {
		return Optional.ofNullable(cache.get(cleartextPath));
	}
}
