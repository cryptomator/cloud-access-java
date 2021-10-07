package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import java.util.Iterator;
import java.util.Optional;

class NodeCache {

	private final CachedNode root = CachedNode.detached("");

	/**
	 * Attempts to retrieve a cached node. An empty response may either be a cache miss or the node does not exist.
	 *
	 * @param path The path of the node
	 * @return Cached node or an empty response in case of non-existing or non-cached nodes.
	 */
	public Optional<CachedNode> getCachedNode(String path) {
		var pathElements = Splitter.on('/').omitEmptyStrings().split(path).iterator();
		return Optional.ofNullable(getCachedNode(root, pathElements));
	}

	private CachedNode getCachedNode(CachedNode base, Iterator<String> remainingPathElements) {
		if (base == null || !remainingPathElements.hasNext()) {
			return base;
		} else {
			var next = base.getChild(remainingPathElements.next());
			return getCachedNode(next, remainingPathElements);
		}
	}

	/**
	 * Detaches the node from the tree and marks all ancestors dirty.
	 *
	 * @param path The path of the node
	 */
	public void delete(String path) {
		var node = getCachedNode(path);
		node.ifPresent(this::delete);
	}

	private void delete(CachedNode node) {
		var parent = node.getParent();
		Preconditions.checkArgument(parent != null, "Can not delete root");
		parent.deleteChild(node.getName());
		parent.markDirty();
	}

}
