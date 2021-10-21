package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.function.Function;

class CachedPropfindEntryProviderTest {

	private final PropfindEntryItemData testFileManual = new PropfindEntryItemData.Builder()
			.withPath("/Nextcloud Manual.pdf")
			.withLastModified(Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")))
			.withSize(Optional.of(6837751L))
			.withCollection(false)
			.build();

	private final NodeCache cache = Mockito.mock(NodeCache.class);
	private final Function<CloudPath, PropfindEntryItemData> loader = Mockito.mock(Function.class);
	private CachedPropfindEntryProvider cachedPropfindEntryProvider;

	@BeforeEach
	void setUp() {
		cachedPropfindEntryProvider = new CachedPropfindEntryProvider(cache);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from cache")
	public void testItemMetadataFromCache() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf", testFileManual)));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), loader);

		Assertions.assertEquals(testFileManual, itemMetadata);
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because not cached")
	public void testItemMetadataFromLoaderBecauseNotCached() {
		Mockito.when(loader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(testFileManual);
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), loader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(loader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		// FIXME check if update is called
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader because cached but dirty")
	public void testItemMetadataFromLoaderBecauseCachedButDirty() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));

		Mockito.when(loader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(testFileManual);
		Mockito.when(cache.getOrCreateCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(CachedNode.detached("/Nextcloud Manual.pdf"));

		final var itemMetadata = cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), loader);

		Assertions.assertEquals(testFileManual, itemMetadata);

		Mockito.verify(loader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).getOrCreateCachedNode(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
		// FIXME check if update is called
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf from loader not found throws NotFoundException")
	public void testItemMetadataFromLoaderNotFoundThrowsNotFoundException() {
		Mockito.when(cache.getCachedNode(CloudPath.of("/Nextcloud Manual.pdf"))).thenReturn(Optional.of(CachedNode.detached("/Nextcloud Manual.pdf")));
		Mockito.when(loader.apply(CloudPath.of("/Nextcloud Manual.pdf"))).thenThrow(NotFoundException.class);

		Assertions.assertThrows(NotFoundException.class, () -> {
			cachedPropfindEntryProvider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"), loader);
		});

		Mockito.verify(loader).apply(CloudPath.of("/Nextcloud Manual.pdf"));
		Mockito.verify(cache).delete(ArgumentMatchers.eq(CloudPath.of("/Nextcloud Manual.pdf")));
	}

	// FIXME implement list and move

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
}