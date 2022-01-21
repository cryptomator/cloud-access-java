package org.cryptomator.cloudaccess.requestdecorator;

import org.cryptomator.cloudaccess.api.CloudProvider;

/**
 * Factory class to add a caching or request-deduplication decorator around an existing {@link CloudProvider}.
 */
public class CloudProviderDecoratorFactory {

	public CloudProvider get(CloudProvider cloudProvider, boolean cloudCachingCapability) {
		if (cloudCachingCapability) {
			var quotaCachingDecorator = new QuotaRequestCachingDecorator(cloudProvider);
			return new MetadataRequestDeduplicationDecorator(quotaCachingDecorator);
		} else {
			return new MetadataCachingProviderDecorator(cloudProvider);
		}
	}
}
