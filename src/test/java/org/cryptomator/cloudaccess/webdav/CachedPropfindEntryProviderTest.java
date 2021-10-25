package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

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
	private final Function<CloudPath, PropfindEntryItemData> singleItemLoader = Mockito.mock(Function.class);
	private final Function<CloudPath, List<PropfindEntryItemData>> multiItemLoader = Mockito.mock(Function.class);
	private CachedPropfindEntryProvider cachedPropfindEntryProvider;

	@BeforeEach
	public void setUp() {
		cachedPropfindEntryProvider = new CachedPropfindEntryProvider(cache);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from cache")
	public void testItemMetadataFromCache() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf", testFileManual)));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), singleItemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because not cached")
	public void testItemMetadataFromLoaderBecauseNotCached() {
		Mockito.when(singleItemLoader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(testFileManual);
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), singleItemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(singleItemLoader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		// FIXME check if update is called
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because cached but dirty")
	public void testItemMetadataFromLoaderBecauseCachedButDirty() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));

		Mockito.when(singleItemLoader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(testFileManual);
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), singleItemLoader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(singleItemLoader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		// FIXME check if update is called
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader not found throws NotFoundException")
	public void testItemMetadataFromLoaderNotFoundThrowsNotFoundException() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));
		Mockito.when(singleItemLoader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenThrow(NotFoundException.class);

		Assertions.assertThrows(NotFoundException.class, () -> {
			cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), singleItemLoader);
		});

		Mockito.verify(singleItemLoader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).delete(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	@Test
	@DisplayName("list / from loader cache")
	public void testListFromCache() {
		var root = CachedNode.detached("/", testFolderRoot);
		root.addChild(CachedNode.detached("/Documents", testFolderDocuments));
		root.addChild(CachedNode.detached("/Nextcloud Manual.pdf", testFileManual));
		root.addChild(CachedNode.detached("/Nextcloud intro.mp4", testFileIntro));
		root.addChild(CachedNode.detached("/Nextcloud.png", testFilePng));
		root.addChild(CachedNode.detached("/Photos", testFolderPhotos));

		Mockito.when(cache.getCachedNode(CloudPath.of("/"))).thenReturn(Optional.of(root));

		final var itemsMetadata = cachedPropfindEntryProvider.list(CloudPath.of("/"), multiItemLoader);

		Assertions.assertEquals(List.of(testFileManual, testFolderDocuments, testFilePng, testFileIntro, testFolderPhotos), itemsMetadata);
	}

	@Test
	@DisplayName("list / from loader as not cached")
	public void testListFromLoaderBecauseNotCached() {
		Mockito.when(multiItemLoader.apply(CloudPath.of("/"))).thenReturn(Arrays.asList(testFolderRoot, testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/"))).thenReturn(CachedNode.detached("/"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Documents"))).thenReturn(CachedNode.detached("/Documents"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud intro.mp4"))).thenReturn(CachedNode.detached("/Nextcloud intro.mp4"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud.png"))).thenReturn(CachedNode.detached("/Nextcloud.png"));
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Photos"))).thenReturn(CachedNode.detached("/Photos"));

		final var itemsMetadata = cachedPropfindEntryProvider.list(CloudPath.of("/"), multiItemLoader);

		Assertions.assertEquals(List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos), itemsMetadata);

		Mockito.verify(multiItemLoader).apply(CloudPath.of("/"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Documents")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud intro.mp4")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud.png")));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Photos")));
	}

	@Test
	@DisplayName("write /Nextcloud Manual.pdf marks cache dirty")
	public void testWriteMarksDirty() {
		cachedPropfindEntryProvider.write(CloudPath.of("/Nextcloud Manual.pdf"));
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