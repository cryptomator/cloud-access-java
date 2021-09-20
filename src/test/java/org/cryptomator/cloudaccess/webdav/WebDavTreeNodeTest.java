package org.cryptomator.cloudaccess.webdav;

import org.junit.Test;

public class WebDavTreeNodeTest {

	@Test
	public void fooTest() {
		var root = new WebDavTreeNode<PropfindEntryItemData>("");
		var foo = new WebDavTreeNode<PropfindEntryItemData>("foo");
		var bar = new WebDavTreeNode<PropfindEntryItemData>("bar");
		var baz = new WebDavTreeNode<PropfindEntryItemData>("baz");

		//foo.addChild(grandChild);
		root.addChild(foo);
		foo.addChild(bar);
		foo.addChild(baz);

		foo.getChildren().get(0).setData(new PropfindEntryItemData.Builder().withEtag("foo").build());

		System.out.println(root);
	}

}