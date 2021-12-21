package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cryptolib.api.FileHeader;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

class FileHeaderCache {

	private final Cache<CloudPath, CompletionStage<FileHeader>> cache;

	FileHeaderCache(int timeoutMillis) {
		this.cache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMillis(timeoutMillis)).build();
	}

	public synchronized CompletionStage<FileHeader> get(CloudPath ciphertextPath, Function<CloudPath, CompletionStage<FileHeader>> onMiss) {
		try {
			return cache.get(ciphertextPath, () -> onMiss.apply(ciphertextPath));
		} catch (ExecutionException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public synchronized void evict(CloudPath ciphertextPath) {
		cache.invalidate(ciphertextPath);
	}

}
