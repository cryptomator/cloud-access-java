package org.cryptomator.cloudaccess.webdav;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CachedNodeTest {

	private CachedNode root = CachedNode.detached("");

	@Test
	public void testAddChild() {
		var detachedFoo = CachedNode.detached("foo");

		var foo = root.addChild(detachedFoo);

		Assertions.assertNotEquals(foo, detachedFoo);
		Assertions.assertEquals(root, foo.getParent());
		Assertions.assertEquals(foo, root.getChild("foo"));
	}

	@Test
	public void testAddParentAsChild() {
		var foo = root.addChild(CachedNode.detached("foo"));

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			foo.addChild(root);
		});
	}

	@Test
	public void testSetAndGetData() {
		var node = CachedNode.detached("foo");
		var data = new PropfindEntryItemData.Builder().withEtag("foo").build();

		node.setData(data);

		Assertions.assertEquals(data, node.getData());
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

		@Test
		public void testMarkDirty() {
			Assumptions.assumeFalse(root.isDirty());
			Assumptions.assumeFalse(foo.isDirty());
			Assumptions.assumeFalse(fooBar.isDirty());
			Assumptions.assumeFalse(fooBaz.isDirty());

			fooBar.markDirty();

			Assertions.assertTrue(root.isDirty());
			Assertions.assertTrue(foo.isDirty());
			Assertions.assertTrue(fooBar.isDirty());
			Assertions.assertFalse(fooBaz.isDirty());
		}

		@Test
		@Disabled("just meaningful for humans")
		public void printTree() {
			System.out.println(CachedNodePrettyPrinter.prettyPrint(root));
		}

	}

}