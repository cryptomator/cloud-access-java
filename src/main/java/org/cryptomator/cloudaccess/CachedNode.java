package org.cryptomator.cloudaccess;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A node in a tree of cached data. A node can be marked dirty if
 * <ul>
 *     <li>either no data has been associated with this node yet</li>
 *     <li>or the node's data is known to be outdated and this node has been {@link #markDirty() marked dirty} explicitly</li>
 * </ul>
 *
 * A dirty node can still be read but its data should be considered unreliable and
 * should be {@link #update(Cachable) updated} as soon as possible.
 */
public class CachedNode {

	private final String name;
	private final CachedNode parent;
	private final Map<String, CachedNode> children;
	private boolean dirty;
	private boolean childrenFetched;
	private Cachable<?> data;

	@FunctionalInterface
	public interface Cachable<T extends Cachable<T>> {

		boolean isSameVersion(T other);

	}

	public static CachedNode detached(String name) {
		return new CachedNode(null, name, null, new ConcurrentHashMap<>(), false, true);
	}

	private CachedNode(CachedNode parent, String name, Cachable<?> data, Map<String, CachedNode> children, boolean childrenFetched, boolean dirty) {
		this.parent = parent;
		this.name = Objects.requireNonNull(name);
		this.children = Objects.requireNonNull(children);
		this.childrenFetched = childrenFetched;
		this.data = data;
		this.dirty = dirty;
	}

	public String getName() {
		return name;
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
		return addChild(node, node.name);
	}

	public CachedNode addChild(CachedNode node, String name) {
		Preconditions.checkArgument(!this.isAncestor(node), "can not add ancestor as child");
		var child = new CachedNode(this, name, node.data, node.children, node.childrenFetched, node.isDirty());
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

	/**
	 * Sets the cached data and drops the dirty bit.
	 * @param data The data to be cached
	 * @see #markDirty()
	 */
	public void update(Cachable<?> data) {
		this.data = data;
		this.dirty = false;
	}

	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Marks the cached data as out of date until it gets {@link #update(Cachable) updated}.
	 *
	 * @see #update(Cachable)
	 */
	public void markDirty() {
		this.dirty = true;
	}

	public boolean isChildrenFetched() {
		return childrenFetched;
	}

	public void setChildrenFetched() {
		childrenFetched = true;
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

