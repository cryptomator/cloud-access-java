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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MetadataRequestDeduplicationDecoratorTest {

	private final CloudPath file1 = CloudPath.of("/foo");
	private final CloudItemMetadata itemMetadata = new CloudItemMetadata(file1.getFileName().toString(), file1, CloudItemType.FILE);
	private final CloudItemList itemList = new CloudItemList(List.of(itemMetadata));
	private final Optional<String> pageToken = Optional.empty();

	private CloudProvider cloudProvider;
	private MetadataRequestDeduplicationDecorator decorator;

	private CompletableFuture<CloudItemMetadata> futureItemMetadata1;
	private CompletableFuture<CloudItemMetadata> futureItemMetadata2;
	private CompletableFuture<CloudItemList> futureItemList1;
	private CompletableFuture<CloudItemList> futureItemList2;

	@BeforeEach
	public void setup() {
		cloudProvider = Mockito.mock(CloudProvider.class);
		AsyncCache<CloudPath, CloudItemMetadata> cachedItemMetadataRequests = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync();
		AsyncCache<MetadataRequestDeduplicationDecorator.ItemListEntry, CloudItemList> cachedItemListRequests = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(0)).buildAsync();
		decorator = new MetadataRequestDeduplicationDecorator(cloudProvider, cachedItemMetadataRequests, cachedItemListRequests);

		futureItemMetadata1 = new CompletableFuture<>();
		futureItemMetadata2 = new CompletableFuture<>();
		futureItemList1 = new CompletableFuture<>();
		futureItemList2 = new CompletableFuture<>();
	}

	@Test
	@DisplayName("Same CompletionStage<CloudItemMetadata> is returned as long as the future has not completed for itemMetadata")
	public void testSameCompletionStageReturnedForItemMetadata() {
		Mockito.doReturn(futureItemMetadata1, futureItemMetadata2).when(cloudProvider).itemMetadata(file1);

		var result1 = decorator.itemMetadata(file1);
		var result2 = decorator.itemMetadata(file1);

		futureItemMetadata1.complete(itemMetadata);

		Assertions.assertSame(result1, result2);
		Mockito.verify(cloudProvider, Mockito.atMostOnce()).itemMetadata(file1);
	}

	@Test
	@DisplayName("Different CompletionStage<CloudItemMetadata> is returned after future has completed for itemMetadata")
	public void testDifferentCompletionStageReturnedForItemMetadata() {
		Mockito.doReturn(futureItemMetadata1, futureItemMetadata2).when(cloudProvider).itemMetadata(file1);

		var result1 = decorator.itemMetadata(file1);
		futureItemMetadata1.complete(itemMetadata);
		var result2 = decorator.itemMetadata(file1);

		Assertions.assertNotSame(result1, result2);
		Mockito.verify(cloudProvider, Mockito.times(2)).itemMetadata(file1);
	}

	@Test
	@DisplayName("Same CompletionStage<CloudItemList> is returned as long as the future has not completed for list")
	public void testSameCompletionStageReturnedForList() {
		Mockito.doReturn(futureItemList1, futureItemList2).when(cloudProvider).list(file1, pageToken);

		var result1 = decorator.list(file1, pageToken);
		var result2 = decorator.list(file1, pageToken);

		futureItemList1.complete(itemList);

		Assertions.assertSame(result1, result2);
		Mockito.verify(cloudProvider, Mockito.atMostOnce()).list(file1, pageToken);
	}

	@Test
	@DisplayName("Different CompletionStage<CloudItemList> is returned after future has completed for list")
	public void testDifferentCompletionStageReturnedForList() {
		Mockito.doReturn(futureItemList1, futureItemList2).when(cloudProvider).list(file1, pageToken);

		var result1 = decorator.list(file1, pageToken);
		futureItemList1.complete(itemList);
		var result2 = decorator.list(file1, pageToken);

		Assertions.assertNotSame(result1, result2);
		Mockito.verify(cloudProvider, Mockito.times(2)).list(file1, pageToken);
	}

}