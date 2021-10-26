package org.cryptomator.cloudaccess.webdav;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CachedNodeTest {

	private CachedNode root = CachedNode.detached("");

	@Test
	public void testAddChild() {
		var detachedFoo = CachedNode.detached("foo");

		var foo = root.addChild(detachedFoo);

		Assertions.assertNotEquals(foo, detachedFoo);
		Assertions.assertTrue(foo.isDirty());
		Assertions.assertEquals(root, foo.getParent());
		Assertions.assertEquals(foo, root.getChild("foo"));
	}

	@Test
	public void testAddChildWithSpecificName() {
		var detachedFoo = CachedNode.detached("foo");

		var bar = root.addChild(detachedFoo, "bar");

		Assertions.assertNotEquals(bar, detachedFoo);
		Assertions.assertTrue(bar.isDirty());
		Assertions.assertEquals(root, bar.getParent());
		Assertions.assertEquals(bar, root.getChild("bar"));
	}

	@Test
	public void testAddParentAsChild() {
		var foo = root.addChild(CachedNode.detached("foo"));

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			foo.addChild(root);
		});
	}

	@Test
	public void testUpdateData() {
		var node = CachedNode.detached("foo");
		var etag = Mockito.mock(CachedNode.Cachable.class);

		node.update(etag);

		Assertions.assertEquals(etag, node.getData());
		Assertions.assertFalse(node.isDirty());
	}

	@Nested
	public class WithSomeChildren {

		private CachedNode foo;
		private CachedNode fooBar;
		private CachedNode fooBaz;

		@BeforeEach
		public void setup() {
			this.foo = root.addChild(CachedNode.detached("foo"));
			this.fooBar = foo.addChild(CachedNode.detached("bar"));
			this.fooBaz = foo.addChild(CachedNode.detached("baz"));
		}

		@Test
		public void testGetParent() {
			Assertions.assertEquals(root, foo.getParent());
			Assertions.assertEquals(foo, fooBar.getParent());
			Assertions.assertEquals(foo, fooBaz.getParent());
		}

		@Test
		public void testGetChildren() {
			var newNode = CachedNode.detached("new");

			var children = foo.getChildren();

			Assertions.assertTrue(children.contains(fooBar));
			Assertions.assertTrue(children.contains(fooBaz));
			Assertions.assertThrows(UnsupportedOperationException.class, () -> {
				children.add(newNode);
			});
		}

		@Test
		public void testGetChildByName() {
			var node = root.getChild("foo").getChild("bar");

			Assertions.assertEquals(fooBar, node);
		}

		@Nested
		public class WithUpdatedData {

			@BeforeEach
			public void setup() {
				var etag1 = Mockito.mock(CachedNode.Cachable.class);
				var etag2 = Mockito.mock(CachedNode.Cachable.class);
				var etag3 = Mockito.mock(CachedNode.Cachable.class);
				var etag4 = Mockito.mock(CachedNode.Cachable.class);
				root.update(etag1);
				foo.update(etag2);
				fooBar.update(etag3);
				fooBaz.update(etag4);
			}

			@Test
			public void testNonDirty() {
				Assertions.assertFalse(root.isDirty());
				Assertions.assertFalse(foo.isDirty());
				Assertions.assertFalse(fooBar.isDirty());
				Assertions.assertFalse(fooBaz.isDirty());
			}

			@Test
			public void testMarkDirty() {
				fooBar.markDirty();

				Assertions.assertFalse(root.isDirty());
				Assertions.assertFalse(foo.isDirty());
				Assertions.assertTrue(fooBar.isDirty());
				Assertions.assertFalse(fooBaz.isDirty());
			}

		}

		@Test
		@Disabled("just meaningful for humans")
		public void printTree() {
			System.out.println(CachedNodePrettyPrinter.prettyPrint(root));
		}

	}

}