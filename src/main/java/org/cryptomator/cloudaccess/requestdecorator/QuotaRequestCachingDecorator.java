package org.cryptomator.cloudaccess.requestdecorator;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.Quota;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.QuotaNotAvailableException;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Decorates an existing CloudProvider by caching quota-requests for a duration of default 10 seconds (can be set using <code>org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds</code>).
 */
class QuotaRequestCachingDecorator implements CloudProviderDecorator {

	private final static int DEFAULT_CACHE_TIMEOUT_SECONDS = 10;

	// visible for testing
	final AsyncCache<CloudPath, Quota> quotaCache;

	private final CloudProvider delegate;

	public QuotaRequestCachingDecorator(CloudProvider delegate) {
		this(delegate, Caffeine
				.newBuilder()
				.expireAfterWrite(Duration.ofSeconds(Integer.getInteger("org.cryptomator.cloudaccess.metadatacachingprovider.timeoutSeconds", DEFAULT_CACHE_TIMEOUT_SECONDS)))
				.buildAsync());
	}

	QuotaRequestCachingDecorator(CloudProvider delegate, AsyncCache<CloudPath, Quota> quotaCache) {
		this.delegate = delegate;
		this.quotaCache = quotaCache;
	}

	@Override
	public CloudProvider delegate() {
		return delegate;
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		return quotaCache.get(folder, k -> delegate.quota(k).whenComplete((metadata, throwable) -> {
			if (throwable != null && !(throwable instanceof NotFoundException) && !(throwable instanceof QuotaNotAvailableException)) {
				quotaCache.synchronous().invalidate(folder);
			}
		}).toCompletableFuture().join());
	}
}
