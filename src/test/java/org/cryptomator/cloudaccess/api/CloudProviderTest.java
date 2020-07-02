package org.cryptomator.cloudaccess.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CloudProviderTest {
	
	@Test
	public void testListExhaustively() {
		Path p = Mockito.mock(Path.class, "path");
		CloudItemMetadata item1 = Mockito.mock(CloudItemMetadata.class, "item1");
		CloudItemMetadata item2 = Mockito.mock(CloudItemMetadata.class, "item2");
		CloudItemMetadata item3 = Mockito.mock(CloudItemMetadata.class, "item3");
		CloudItemMetadata item4 = Mockito.mock(CloudItemMetadata.class, "item4");
		CloudItemMetadata item5 = Mockito.mock(CloudItemMetadata.class, "item5");
		CloudItemMetadata item6 = Mockito.mock(CloudItemMetadata.class, "item6");
		CloudItemList part1 = new CloudItemList(Arrays.asList(item1, item2, item3), Optional.of("token1"));
		CloudItemList part2 = new CloudItemList(Arrays.asList(item4, item5), Optional.of("token2"));
		CloudItemList part3 = new CloudItemList(Arrays.asList(item6), Optional.empty());
		CloudProvider provider = Mockito.mock(CloudProvider.class);
		Mockito.when(provider.listExhaustively(p)).thenCallRealMethod();
		Mockito.when(provider.list(p, Optional.empty())).thenReturn(CompletableFuture.completedFuture(part1));
		Mockito.when(provider.list(p, part1.getNextPageToken())).thenReturn(CompletableFuture.completedFuture(part2));
		Mockito.when(provider.list(p, part2.getNextPageToken())).thenReturn(CompletableFuture.completedFuture(part3));
		
		CompletionStage<CloudItemList> result = provider.listExhaustively(p);
		
		Assertions.assertNotNull(result);
		CloudItemList itemList = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->  result.toCompletableFuture().get());
		Assertions.assertFalse(itemList.getNextPageToken().isPresent());
		MatcherAssert.assertThat(itemList.getItems(), CoreMatchers.hasItems(item1, item2, item3, item4, item5, item6));
	}
	
	@Test
	public void testRead() {
		Path p = Mockito.mock(Path.class, "path");
		ProgressListener l = Mockito.mock(ProgressListener.class, "listener");
		CloudProvider provider = Mockito.mock(CloudProvider.class);
		Mockito.when(provider.read(p, l)).thenCallRealMethod();
		
		provider.read(p, l);
		
		Mockito.verify(provider).read(p, 0l, Long.MAX_VALUE, l);
	}
	
}
