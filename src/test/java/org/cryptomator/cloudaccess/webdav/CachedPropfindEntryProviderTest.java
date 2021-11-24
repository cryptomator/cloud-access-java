package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.CachedNode;
import org.cryptomator.cloudaccess.NodeCache;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class CachedPropfindEntryProviderTest {

	private final PropfindEntryItemData testFolderRoot = new PropfindEntryItemData.Builder()
			.withPath("/")
			.withCollection(true)
			.build();

	private final PropfindEntryItemData testFolderDocuments = new PropfindEntryItemData.Builder()
			.withPath("/Documents")
			.withCollection(true)
			.build();

	private final PropfindEntryItemData testFileManual = new PropfindEntryItemData.Builder()
			.withPath("/Nextcloud Manual.pdf")
			.withLastModified(Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")))
			.withSize(Optional.of(6837751L))
			.withCollection(false)
			.build();

	private final PropfindEntryItemData testFileIntro = new PropfindEntryItemData.Builder()
			.withPath("/Nextcloud intro.mp4")
			.withLastModified(Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")))
			.withSize(Optional.of(462413L))
			.withCollection(false)
			.build();

	private final PropfindEntryItemData testFilePng = new PropfindEntryItemData.Builder()
			.withPath("/Nextcloud.png")
			.withLastModified(Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")))
			.withSize(Optional.of(37042L))
			.withCollection(false)
			.build();

	private final PropfindEntryItemData testFolderPhotos = new PropfindEntryItemData.Builder()
			.withPath("/Photos")
			.withCollection(true)
			.build();

	private final NodeCache cache = Mockito.mock(NodeCache.class);
	private final Function<CloudPath, List<PropfindEntryItemData>> itemLoader = Mockito.mock(Function.class);
	private final Function<CloudPath, List<PropfindEntryItemData>> parentLoader = Mockito.mock(Function.class);
	private CachedPropfindEntryProvider cachedPropfindEntryProvider;

	@BeforeEach
	public void setUp() {
		cachedPropfindEntryProvider = new CachedPropfindEntryProvider(cache);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from cache")
	public void testItemMetadataFromCache() {
		var manual = CachedNode.detached("/Nextcloud Manual.pdf");
		manual.update(testFileManual);

		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(manual));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because not cached and parent loaded")
	public void testItemMetadataFromLoaderBecauseNotCachedAndParentLoaded() {
		var root = CachedNode.detached("/");
		root.update(testFolderRoot);
		root.setChildrenFetched();
		var manual = CachedNode.detached("/Nextcloud Manual.pdf");
		root.addChild(manual);

		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(root));
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(manual));

		Mockito.when(itemLoader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Arrays.asList(testFileManual));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(itemLoader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache, Mockito.times(2)).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from parent loader because not cached and parent not loaded")
	public void testItemMetadataFromParentLoaderBecauseNotCachedAndParentNotLoaded() {
		Mockito.when(parentLoader.apply(CloudPath.of("/"))).thenReturn(Arrays.asList(testFolderRoot, testFileManual));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(parentLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache, Mockito.times(2)).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because cached but dirty")
	public void testItemMetadataFromLoaderBecauseCachedButDirty() {
		var root = CachedNode.detached("/");
		root.update(testFolderRoot);
		root.setChildrenFetched();
		var manual = CachedNode.detached("/Nextcloud Manual.pdf");
		manual.update(testFileManual);
		manual.markDirty();
		root.addChild(manual);

		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(root));
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(manual));

		Mockito.when(itemLoader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Arrays.asList(testFileManual));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(itemLoader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache, Mockito.times(2)).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from parent loader because cached but dirty")
	public void testItemMetadataFromParentLoaderBecauseCachedButDirty() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));

		Mockito.when(parentLoader.apply(CloudPath.of("/"))).thenReturn(Arrays.asList(testFolderRoot, testFileManual));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(parentLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache, Mockito.times(2)).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from parent loader not found throws NotFoundException")
	public void testItemMetadataFromParentLoaderNotFoundThrowsNotFoundException() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(CachedNode.detached("/")));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));
		Mockito.when(parentLoader.apply(CloudPath.of("/"))).thenReturn(Arrays.asList(testFolderRoot));

		Assertions.assertThrows(NotFoundException.class, () -> {
			cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), parentLoader, itemLoader);
		});

		Mockito.verify(parentLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache).deleteAndMarkDirtyIfPresent(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("get metadata of / from loader not found throws NotFoundException")
	public void testItemMetadataFromLoaderNotFoundThrowsNotFoundException() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(CachedNode.detached("/")));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));

		Mockito.when(itemLoader.apply(CloudPath.of("/"))).thenThrow(NotFoundException.class);

		Assertions.assertThrows(NotFoundException.class, () -> {
			cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/"), parentLoader, itemLoader);
		});

		Mockito.verify(itemLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache).deleteAndMarkDirtyIfPresent(ArgumentMatchers.eq(CloudPath.of("/")));
	}

	@Test
	@DisplayName("list / from cache")
	public void testListFromCache() {
		var root = CachedNode.detached("/");
		root.update(testFolderRoot);
		root.setChildrenFetched();
		var documents = CachedNode.detached("/Documents");
		documents.update(testFolderDocuments);
		var manual = CachedNode.detached("/Nextcloud Manual.pdf");
		manual.update(testFileManual);
		var intro = CachedNode.detached("/Nextcloud intro.mp4");
		intro.update(testFileIntro);
		var png = CachedNode.detached("/Nextcloud.png");
		png.update(testFilePng);
		var photos = CachedNode.detached("/Photos");
		photos.update(testFolderPhotos);

		root.addChild(documents);
		root.addChild(manual);
		root.addChild(intro);
		root.addChild(png);
		root.addChild(photos);

		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(root));

		final var itemsMetadata = cachedPropfindEntryProvider.list(CloudPath.of("/"), itemLoader);

		Assertions.assertEquals(List.of(testFileManual, testFolderDocuments, testFilePng, testFileIntro, testFolderPhotos), itemsMetadata);
	}

	@Test
	@DisplayName("list / from loader as not cached")
	public void testListFromLoaderBecauseNotCached() {
		Mockito.when(itemLoader.apply(CloudPath.of("/"))).thenReturn(Arrays.asList(testFolderRoot, testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Documents"))).thenReturn(CachedNode.detached("/Documents"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud intro.mp4"))).thenReturn(CachedNode.detached("/Nextcloud intro.mp4"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud.png"))).thenReturn(CachedNode.detached("/Nextcloud.png"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Photos"))).thenReturn(CachedNode.detached("/Photos"));

		final var itemsMetadata = cachedPropfindEntryProvider.list(CloudPath.of("/"), itemLoader);

		Assertions.assertEquals(List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos), itemsMetadata);

		Mockito.verify(itemLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache, Mockito.times(2)).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Documents")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud intro.mp4")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud.png")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Photos")));
	}

	@Test
	@DisplayName("write /Nextcloud Manual.pdf with ETag marks only parent dirty")
	public void testWriteMarksPartentsDirtyWithEmptyEtag() {
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		cachedPropfindEntryProvider.write(CloudPath.of("/Nextcloud Manual.pdf"), 15, Optional.of(Instant.now()), Optional.of("ETag3000"));

		Mockito.verify(cache).markDirty(CloudPath.of("/"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("write /Nextcloud Manual.pdf marks cache dirty")
	public void testWriteMarksDirty() {
		cachedPropfindEntryProvider.write(CloudPath.of("/Nextcloud Manual.pdf"), 15, Optional.empty(), Optional.empty());
		Mockito.verify(cache).markDirty(CloudPath.of("/Nextcloud Manual.pdf"));
	}

	@Test
	@DisplayName("create folder /Nextcloud marks cache as dirty")
	public void testCreateFolderDirty() {
		cachedPropfindEntryProvider.createFolder(CloudPath.of("/Nextcloud"));
		Mockito.verify(cache).markDirty(CloudPath.of("/Nextcloud"));
	}

	@Test
	@DisplayName("delete folder /Nextcloud deletes cached entries")
	public void testDeleteFolderDeletesCache() {
		cachedPropfindEntryProvider.delete(CloudPath.of("/Nextcloud"));
		Mockito.verify(cache).delete(CloudPath.of("/Nextcloud"));
	}

	@Test
	@DisplayName("move /Nextcloud Manual.pdf -> /Nextcloud Manual 2.pdf")
	public void testMove() {
		cachedPropfindEntryProvider.move(CloudPath.of("/Nextcloud Manual.pdf"), CloudPath.of("/Nextcloud Manual 2.pdf"));
		Mockito.verify(cache).move(CloudPath.of("/Nextcloud Manual.pdf"), CloudPath.of("/Nextcloud Manual 2.pdf"));
	}
}