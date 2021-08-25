package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;

public class WebDavTreeNode {

	private final String name;
	private final List<WebDavTreeNode> children = new ArrayList<>();

	private WebDavTreeNode parent;
	private PropfindEntryItemData data;

	public WebDavTreeNode(String name) {
		this.name = name;
	}

	public WebDavTreeNode(String name, PropfindEntryItemData data) {
		this.data = data;
		this.name = name;
	}

	public List<WebDavTreeNode> getChildren() {
		return children;
	}

	public WebDavTreeNode getParent() {
		return parent;
	}

	public void setParent(WebDavTreeNode parent) {
		this.parent = parent;
	}

	public WebDavTreeNode addChild(WebDavTreeNode child) {
		child.setParent(this);
		children.add(child);
		return child;
	}

	public void deleteNode() {
		if (parent != null) {
			var index = parent.getChildren().indexOf(this);
			parent.getChildren().remove(this);
			for (WebDavTreeNode node : getChildren()) {
				node.setParent(this.parent);
			}
			parent.getChildren().addAll(index, getChildren());
		} else {
			deleteRootNode();
		}
		getChildren().clear();
	}

	public WebDavTreeNode deleteRootNode() {
		if (parent != null) {
			throw new IllegalStateException("deleteRootNode not called on root");
		}
		WebDavTreeNode newParent = null;
		if (!getChildren().isEmpty()) {
			newParent = getChildren().get(0);
			newParent.setParent(null);
			getChildren().remove(0);
			for (WebDavTreeNode node : getChildren()) {
				node.setParent(newParent);
			}
			newParent.getChildren().addAll(getChildren());
		}
		getChildren().clear();
		return newParent;
	}

	public PropfindEntryItemData getData() {
		return data;
	}

	public void setData(PropfindEntryItemData data) {
		this.data = data;
	}

	public String getName() {
		return name;
	}

	public boolean isRootNode() {
		return (parent == null);
	}

	public boolean isLeafNode() {
		return children.size() == 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		var that = (WebDavTreeNode) o;
		return Objects.equal(name, that.name) && Objects.equal(parent, that.parent) /*&& Objects.equal(children, that.children)*/;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name, children, parent, data);
	}
}

