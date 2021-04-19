package org.cryptomator.cloudaccess.api;

import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CloudProviderTest {

	@Test
	public void testListExhaustively() {
		CloudPath p = Mockito.mock(CloudPath.class, "path");
		CloudItemMetadata item1 = Mockito.mock(CloudItemMetadata.class, "item1");
		CloudItemMetadata item2 = Mockito.mock(CloudItemMetadata.class, "item2");
		CloudItemMetadata item3 = Mockito.mock(CloudItemMetadata.class, "item3");
		CloudItemMetadata item4 = Mockito.mock(CloudItemMetadata.class, "item4");
		CloudItemMetadata item5 = Mockito.mock(CloudItemMetadata.class, "item5");
		CloudItemMetadata item6 = Mockito.mock(CloudItemMetadata.class, "item6");
		CloudItemList part1 = new CloudItemList(List.of(item1, item2, item3), Optional.of("token1"));
		CloudItemList part2 = new CloudItemList(List.of(item4, item5), Optional.of("token2"));
		CloudItemList part3 = new CloudItemList(List.of(item6), Optional.empty());
		CloudProvider provider = Mockito.mock(CloudProvider.class);
		Mockito.when(provider.listExhaustively(p)).thenCallRealMethod();
		Mockito.when(provider.list(p, Optional.empty())).thenReturn(CompletableFuture.completedStage(part1));
		Mockito.when(provider.list(p, part1.getNextPageToken())).thenReturn(CompletableFuture.completedStage(part2));
		Mockito.when(provider.list(p, part2.getNextPageToken())).thenReturn(CompletableFuture.completedStage(part3));

		var result = provider.listExhaustively(p);
		var itemList = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertNotNull(result);
		Assertions.assertTrue(itemList.getNextPageToken().isEmpty());
		MatcherAssert.assertThat(itemList.getItems(), CoreMatchers.hasItems(item1, item2, item3, item4, item5, item6));
	}

	@Test
	public void testRead() {
		CloudPath p = Mockito.mock(CloudPath.class, "path");
		ProgressListener l = Mockito.mock(ProgressListener.class, "listener");
		CloudProvider provider = Mockito.mock(CloudProvider.class);
		Mockito.when(provider.read(p, l)).thenCallRealMethod();

		provider.read(p, l);

		Mockito.verify(provider).read(p, 0l, Long.MAX_VALUE, l);
	}

	@Test
	@DisplayName("createFolderIfNonExisting() for non-existing dir")
	public void testCreateFolderIfNonExisting1() {
		var provider = Mockito.mock(CloudProvider.class);
		var path = Mockito.mock(CloudPath.class, "/path/to/dir");
		Mockito.when(provider.createFolder(path)).thenReturn(CompletableFuture.completedFuture(path));
		Mockito.when(provider.createFolderIfNonExisting(Mockito.any())).thenCallRealMethod();

		var futureResult = provider.createFolderIfNonExisting(path);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(path, result);
	}

	@Test
	@DisplayName("createFolderIfNonExisting() for existing dir")
	public void testCreateFolderIfNonExisting2() {
		var provider = Mockito.mock(CloudProvider.class);
		var path = Mockito.mock(CloudPath.class, "/path/to/dir");
		var e = new AlreadyExistsException("/path/to/dir already exists");
		Mockito.when(provider.createFolder(path)).thenReturn(CompletableFuture.failedFuture(e));
		Mockito.when(provider.createFolderIfNonExisting(Mockito.any())).thenCallRealMethod();

		var futureResult = provider.createFolderIfNonExisting(path);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> futureResult.toCompletableFuture().get());

		Assertions.assertEquals(path, result);
	}

	@Test
	@DisplayName("exists() for existing node")
	public void testExists1() {
		var provider = Mockito.mock(CloudProvider.class);
		var path = Mockito.mock(CloudPath.class, "/path/to/node");
		var metadata = Mockito.mock(CloudItemMetadata.class);
		Mockito.when(provider.itemMetadata(path)).thenReturn(CompletableFuture.completedFuture(metadata));
		Mockito.when(provider.exists(Mockito.any())).thenCallRealMethod();

		var futureResult = provider.exists(path);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> futureResult.toCompletableFuture().get());

		Assertions.assertTrue(result);
	}

	@Test
	@DisplayName("exists() for non-existing node")
	public void testExists2() {
		var provider = Mockito.mock(CloudProvider.class);
		var path = Mockito.mock(CloudPath.class, "/path/to/node");
		var e = new NotFoundException("/path/to/node");
		Mockito.when(provider.itemMetadata(path)).thenReturn(CompletableFuture.failedFuture(e));
		Mockito.when(provider.exists(Mockito.any())).thenCallRealMethod();

		var futureResult = provider.exists(path);
		var result = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> futureResult.toCompletableFuture().get());

		Assertions.assertFalse(result);
	}

}
