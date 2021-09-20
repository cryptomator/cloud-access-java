package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;

public class WebDavTreeNode<T> {

	private final String name;
	private final List<WebDavTreeNode<T>> children = new ArrayList<>();

	private WebDavTreeNode<T> parent;
	private T data;

	public WebDavTreeNode(String name) {
		this.name = name;
	}

	public WebDavTreeNode(String name, T data) {
		this.data = data;
		this.name = name;
	}

	public List<WebDavTreeNode<T>> getChildren() {
		return children;
	}

	public WebDavTreeNode<T> getParent() {
		return parent;
	}

	public void setParent(WebDavTreeNode<T> parent) {
		this.parent = parent;
	}

	public WebDavTreeNode<T> addChild(WebDavTreeNode<T> child) {
		child.setParent(this);
		children.add(child);
		return child;
	}

	public void deleteNode() {
		if (parent != null) {
			var index = parent.getChildren().indexOf(this);
			parent.getChildren().remove(this);
			for (WebDavTreeNode<T> node : getChildren()) {
				node.setParent(this.parent);
			}
			parent.getChildren().addAll(index, getChildren());
		} else {
			deleteRootNode();
		}
		getChildren().clear();
	}

	public WebDavTreeNode<T> deleteRootNode() {
		if (parent != null) {
			throw new IllegalStateException("deleteRootNode not called on root");
		}
		WebDavTreeNode<T> newParent = null;
		if (!getChildren().isEmpty()) {
			newParent = getChildren().get(0);
			newParent.setParent(null);
			getChildren().remove(0);
			for (WebDavTreeNode<T> node : getChildren()) {
				node.setParent(newParent);
			}
			newParent.getChildren().addAll(getChildren());
		}
		getChildren().clear();
		return newParent;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
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
		WebDavTreeNode<?> that = (WebDavTreeNode<?>) o;
		return Objects.equal(name, that.name) && Objects.equal(parent, that.parent) && Objects.equal(data, that.data);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name, children, parent, data);
	}
}

