package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cryptolib.api.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

class FileHeaderCache {

	private static final Duration TIMEOUT = Duration.ofMillis(1000);
	private static final Logger LOG = LoggerFactory.getLogger(FileHeaderCache.class);

	private final Cache<Path, FileHeader> cache = CacheBuilder.newBuilder().expireAfterAccess(TIMEOUT).build();

	public CompletionStage<FileHeader> get(Path ciphertextPath, Function<Path, CompletionStage<FileHeader>> onMiss) {
		var cached = cache.getIfPresent(ciphertextPath);
		if (cached != null) {
			LOG.trace("Cache hit for {}", ciphertextPath);
			return CompletableFuture.completedFuture(cached);
		} else {
			LOG.trace("Cache miss for {}", ciphertextPath);
			return onMiss.apply(ciphertextPath).thenApply(fileHeader -> {
				cache.put(ciphertextPath, fileHeader);
				return fileHeader;
			});
		}
	}


}
