package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class WebDavTreeNode {

	private final String name;
	private final WebDavTreeNode parent;
	private final Map<String, WebDavTreeNode> children;
	private boolean dirty;
	private Cachable<?> data;

	@FunctionalInterface
	interface Cachable<T extends Cachable<T>> {

		boolean isSameVersion(T other);

	}

	public static WebDavTreeNode detached(String name) {
		return new WebDavTreeNode(null, name, null, new HashMap<>());
	}

	private WebDavTreeNode(WebDavTreeNode parent, String name, Cachable<?> data, Map<String, WebDavTreeNode> children) {
		this.parent = parent;
		this.name = Objects.requireNonNull(name);
		this.children = Objects.requireNonNull(children);
		this.data = data;
	}

	public WebDavTreeNode getParent() {
		return parent;
	}

	public boolean isAncestor(WebDavTreeNode node) {
		if (parent == null) {
			return false;
		} else if (parent.equals(node)) {
			return true;
		} else {
			return parent.isAncestor(node);
		}
	}

	public WebDavTreeNode addChild(WebDavTreeNode node) {
		Preconditions.checkArgument(!this.isAncestor(node), "can not add ancestor as child");
		var child = new WebDavTreeNode(this, node.name, node.data, node.children);
		children.put(child.name, child);
		return child;
	}

	public Collection<WebDavTreeNode> getChildren() {
		// prevent modifications that bypass safety checks provided by #addChild(...)
		return Collections.unmodifiableCollection(children.values());
	}

	public WebDavTreeNode getChild(String named) {
		return children.get(named);
	}

	public <T extends Cachable<T>> T getData(Class<T> expectedType) {
		return expectedType.cast(data);
	}

	public Cachable<?> getData() {
		return data;
	}

	public void setData(Cachable<?> data) {
		this.data = data;
	}

	public String getName() {
		return name;
	}

	@Deprecated // TODO: needed?
	public boolean isRootNode() {
		return (parent == null);
	}

	@Deprecated // TODO: needed?
	public boolean isLeafNode() {
		return children.size() == 0;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markDirty() {
		this.dirty = true;
		if (parent != null) {
			parent.markDirty();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		WebDavTreeNode that = (WebDavTreeNode) o;
		// beware of infinite recursion: identity must not deeply depend on parent AND children
		return Objects.equals(name, that.name) && Objects.equals(parent, that.parent) && Objects.equals(data, that.data);
	}

	@Override
	public int hashCode() {
		// beware of infinite recursion: identity must not deeply depend on parent AND children
		return Objects.hash(name, parent, data);
	}
}

