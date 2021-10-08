package org.cryptomator.cloudaccess.webdav;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class NodeCacheTest {

	private NodeCache cache;

	@BeforeEach
	public void setup() {
		// populate cache with some entries:
		cache = new NodeCache();
		cache.getCachedNode("/").orElseThrow().addChild(CachedNode.detached("foo"));
		cache.getCachedNode("/foo").orElseThrow().addChild(CachedNode.detached("bar"));
		cache.getCachedNode("/foo").orElseThrow().addChild(CachedNode.detached("baz"));

		cache.getCachedNode("/").get().update(Mockito.mock(CachedNode.Cachable.class));
		cache.getCachedNode("/foo").get().update(Mockito.mock(CachedNode.Cachable.class));
		cache.getCachedNode("/foo/bar").get().update(Mockito.mock(CachedNode.Cachable.class));
		cache.getCachedNode("/foo/baz").get().update(Mockito.mock(CachedNode.Cachable.class));
	}

	@Test
	public void testContainsFooBar() {
		Assertions.assertTrue(cache.getCachedNode("/foo/bar").isPresent());
	}

	@Test
	public void testDelete() {
		cache.delete("/foo/bar");

		Assertions.assertTrue(cache.getCachedNode("/").orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode("/foo").orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode("/foo/baz").orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode("/foo/bar").isEmpty());
	}

	@Test
	public void testMove() {
		cache.move("/foo/baz", "/baz");

		Assertions.assertTrue(cache.getCachedNode("/").orElseThrow().isDirty());
		Assertions.assertTrue(cache.getCachedNode("/foo").orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode("/baz").orElseThrow().isDirty());
		Assertions.assertFalse(cache.getCachedNode("/foo/bar").orElseThrow().isDirty());
	}

}