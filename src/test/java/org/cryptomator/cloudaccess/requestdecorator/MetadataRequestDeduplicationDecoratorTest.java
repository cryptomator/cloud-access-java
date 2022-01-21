package org.cryptomator.cloudaccess.requestdecorator;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

public class MetadataRequestDeduplicationDecoratorTest {

	private final CloudPath file1 = CloudPath.of("/foo");
	private final CloudPath file2 = CloudPath.of("/bar");

	private CloudProvider cloudProvider;
	private MetadataRequestDeduplicationDecorator decorator;

	private AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests;
	private AsyncCache<MetadataRequestDeduplicationDecorator.ItemListEntry, CloudItemList> cachedItemListRequests;

	@BeforeEach
	public void setup() {
		cloudProvider = Mockito.mock(CloudProvider.class);
		cachedItemMetadataRequests = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync();
		cachedItemListRequests = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync();
		decorator = new MetadataRequestDeduplicationDecorator(cloudProvider, cachedItemMetadataRequests, cachedItemListRequests);
	}

	@Test
	@DisplayName("Caffeine cache invalidates automatically and load value for key once")
	public void testCaffeineCacheInvalidatesAutomaticallyAndLoadsValueForKeyOnce() {
		var itemMetadata = new CloudItemMetadata(file1.getFileName().toString(), file1, CloudItemType.FILE);
		var itemMetadata2 = new CloudItemMetadata(file2.getFileName().toString(), file2, CloudItemType.FILE);

		var completionStage1= cachedItemMetadataRequests.get(file1, k -> {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Assertions.fail("Exception thrown during sleep, retry");
			}
			return itemMetadata;
		});

		var completionStage2 = cachedItemMetadataRequests.get(file1, k -> {
			Assertions.fail("Not allowed to invoke a second time until completionStage1 is finished");
			return itemMetadata2;
		});

		var result1 = Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () -> completionStage1.toCompletableFuture().get());
		var result2 = Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () -> completionStage2.toCompletableFuture().get());

		Assertions.assertEquals(result1, itemMetadata);
		Assertions.assertEquals(result2, itemMetadata);

		Assertions.assertNull(cachedItemMetadataRequests.synchronous().getIfPresent(file1));
	}

}