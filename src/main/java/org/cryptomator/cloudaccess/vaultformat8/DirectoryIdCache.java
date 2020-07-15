package org.cryptomator.cloudaccess.vaultformat8;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

class DirectoryIdCache {

	private static final byte[] ROOT_DIR_ID = new byte[0];
	private static final Map<Path, byte[]> ROOT_MAPPINGS = Map.of(Path.of(""), ROOT_DIR_ID, Path.of("/"), ROOT_DIR_ID);

	private final ConcurrentMap<Path, byte[]> cache = new ConcurrentHashMap<>(ROOT_MAPPINGS);

	public CompletionStage<byte[]> get(Path cleartextPath, BiFunction<Path, byte[], CompletionStage<byte[]>> onMiss) {
		var cached = cache.get(cleartextPath);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		} else {
			var parentPath = cleartextPath.getNameCount() == 1 ? Path.of("") : cleartextPath.getParent();
			return get(parentPath, onMiss).thenCompose(parentDirId -> {
				return onMiss.apply(cleartextPath, parentDirId);
			}).thenApply(dirId -> {
				cache.put(cleartextPath, dirId);
				return dirId;
			});
		}
	}

	Optional<byte[]> getCached(Path cleartextPath) {
		return Optional.ofNullable(cache.get(cleartextPath));
	}

}
