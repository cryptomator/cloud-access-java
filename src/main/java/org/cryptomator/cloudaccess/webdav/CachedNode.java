package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class CachedNode {

	private final String name;
	private final CachedNode parent;
	private final Map<String, CachedNode> children;
	private boolean dirty;
	private Cachable<?> data;

	@FunctionalInterface
	interface Cachable<T extends Cachable<T>> {

		boolean isSameVersion(T other);

	}

	public static CachedNode detached(String name) {
		return new CachedNode(null, name, null, new HashMap<>());
	}

	private CachedNode(CachedNode parent, String name, Cachable<?> data, Map<String, CachedNode> children) {
		this.parent = parent;
		this.name = Objects.requireNonNull(name);
		this.children = Objects.requireNonNull(children);
		this.data = data;
	}

	public CachedNode getParent() {
		return parent;
	}

	public boolean isAncestor(CachedNode node) {
		if (parent == null) {
			return false;
		} else if (parent.equals(node)) {
			return true;
		} else {
			return parent.isAncestor(node);
		}
	}

	public CachedNode addChild(CachedNode node) {
		Preconditions.checkArgument(!this.isAncestor(node), "can not add ancestor as child");
		var child = new CachedNode(this, node.name, node.data, node.children);
		children.put(child.name, child);
		return child;
	}

	public CachedNode deleteChild(String name) {
		return children.remove(name);
	}

	public Collection<CachedNode> getChildren() {
		// prevent modifications that bypass safety checks provided by #addChild(...)
		return Collections.unmodifiableCollection(children.values());
	}

	public CachedNode getChild(String named) {
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
		CachedNode that = (CachedNode) o;
		// beware of infinite recursion: identity must not deeply depend on parent AND children
		return Objects.equals(name, that.name) && Objects.equals(parent, that.parent) && Objects.equals(data, that.data);
	}

	@Override
	public int hashCode() {
		// beware of infinite recursion: identity must not deeply depend on parent AND children
		return Objects.hash(name, parent, data);
	}
}

