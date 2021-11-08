package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class NodeCacheTest {

	private NodeCache cache;

	@BeforeEach
	public void setup() {
		// populate cache with some entries:
		cache = new NodeCache();
		cache.getCachedNode(CloudPath.of("")).orElseThrow().addChild(CachedNode.detached("foo"));
		cache.getCachedNode(CloudPath.of("foo")).orElseThrow().addChild(CachedNode.detached("bar"));
		cache.getCachedNode(CloudPath.of("foo")).orElseThrow().addChild(CachedNode.detached("baz"));

		cache.getOrCreateCachedNode(CloudPath.of("/")).update(Mockito.mock(CachedNode.Cachable.class));
		cache.getOrCreateCachedNode(CloudPath.of("/foo")).update(Mockito.mock(CachedNode.Cachable.class));
		cache.getOrCreateCachedNode(CloudPath.of("/foo/bar")).update(Mockito.mock(CachedNode.Cachable.class));
		cache.getOrCreateCachedNode(CloudPath.of("/foo/baz")).update(Mockito.mock(CachedNode.Cachable.class));
	}

	@Test
	@DisplayName("getCachedNode() returns existing cached node")
	public void testGetFooBar() {
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/bar")).isPresent());
	}

	@Test
	@DisplayName("getCachedNode() returns ø for non-cached node")
	public void testGetNonExisting() {
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/non/existing")).isEmpty());
	}

	@Test
	@DisplayName("getOrCreateCachedNode() returns newly created cached node")
	public void testGetOrCreateNonExisting() {
		Assertions.assertNotNull(cache.getOrCreateCachedNode(CloudPath.of("/non/existing")));
	}

	@Test
	@DisplayName("delete() is noop for non-cached nodes")
	public void testDeleteNonExisting() {
		Assertions.assertDoesNotThrow(() -> {
			cache.delete(CloudPath.of("/non/existing"));
		});
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/non")).isEmpty());
	}

	@Test
	@DisplayName("delete() marks ancestors dirty and removes node")
	public void testDelete() {
		cache.delete(CloudPath.of("/foo/bar"));

		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/foo/baz")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/bar")).isEmpty());
	}

	@Test
	@DisplayName("move() marks source and target ancestors dirty but not the moved node itself")
	public void testMove() {
		var moved = cache.move(CloudPath.of("/foo/baz"), CloudPath.of("/baz"));

		Assertions.assertTrue(moved.isPresent());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/baz")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/foo/bar")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/baz")).isEmpty());
	}

	@Test
	@DisplayName("move() marks source and target ancestors dirty even for non-cached nodes")
	public void testMoveNonExisting() {
		var moved = cache.move(CloudPath.of("/foo/bar/non/existing"), CloudPath.of("/dst"));

		Assertions.assertFalse(moved.isPresent());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/bar")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/foo/baz")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/dst")).isEmpty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/bar/non/existing")).isEmpty());
	}

	@Test
	@DisplayName("move() moves all descendants as well")
	public void testMoveIncludingDescendants() {
		cache.getCachedNode(CloudPath.of("")).orElseThrow().addChild(CachedNode.detached("dst"));
		cache.getCachedNode(CloudPath.of("foo/baz")).orElseThrow().addChild(CachedNode.detached("qux"));

		cache.getOrCreateCachedNode(CloudPath.of("/dst")).update(Mockito.mock(CachedNode.Cachable.class));
		cache.getOrCreateCachedNode(CloudPath.of("/foo/baz/qux")).update(Mockito.mock(CachedNode.Cachable.class));

		var moved = cache.move(CloudPath.of("/foo"), CloudPath.of("/dst"));

		Assertions.assertTrue(moved.isPresent());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/dst/bar")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/dst/baz")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/dst/baz/qux")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo")).isEmpty());
	}

	@Test
	@DisplayName("move() which just renames marks source and target ancestors dirty but not the moved node itself")
	public void testRename() {
		var moved = cache.move(CloudPath.of("/foo/baz"), CloudPath.of("/foo/bar"));

		Assertions.assertTrue(moved.isPresent());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo")).orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode(CloudPath.of("/foo/bar")).orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode(CloudPath.of("/foo/baz")).isEmpty());
	}

}