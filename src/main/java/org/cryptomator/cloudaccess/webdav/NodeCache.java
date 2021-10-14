package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
import org.cryptomator.cloudaccess.api.CloudPath;

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
	public Optional<CachedNode> getCachedNode(CloudPath path) {
		var pathElements = path.iterator();
		return Optional.ofNullable(getCachedNode(root, pathElements, false));
	}

	/**
	 * Adds all non-existing nodes along the given path to the cache.
	 *
	 * @param path The path of the node
	 * @return Cached node
	 */
	public CachedNode getOrCreateCachedNode(CloudPath path) {
		var pathElements = path.iterator();
		return getCachedNode(root, pathElements, true);
	}

	private CachedNode getCachedNode(CachedNode base, Iterator<CloudPath> remainingPathElements, boolean create) {
		if (base == null || !remainingPathElements.hasNext()) {
			return base;
		} else {
			var childName = remainingPathElements.next().toString();
			var next = base.getChild(childName);
			if (next == null && create) {
				next = base.addChild(CachedNode.detached(childName));
			}
			return getCachedNode(next, remainingPathElements, create);
		}
	}

	/**
	 * Marks every cached node along the given <code>path</code> as dirty.
	 *
	 * @param path The path of the node
	 */
	public void markDirty(CloudPath path) {
		var pathElements = path.iterator();
		markDirty(root, pathElements);
	}

	private void markDirty(CachedNode node, Iterator<CloudPath> remainingPathElements) {
		node.markDirty();
		if (remainingPathElements.hasNext()) {
			var nextNode = node.getChild(remainingPathElements.next().toString());
			if (nextNode != null) {
				markDirty(nextNode, remainingPathElements);
			}
		}
	}

	/**
	 * Detaches the node from the tree and marks all ancestors dirty.
	 *
	 * @param path The path of the node
	 */
	public void delete(CloudPath path) {
		var node = getCachedNode(path);
		node.ifPresent(this::delete);
		markDirty(path);
	}

	private void delete(CachedNode node) {
		var parent = node.getParent();
		Preconditions.checkArgument(parent != null, "Can not delete root");
		parent.deleteChild(node.getName());
	}

	/**
	 * Attempts to move a cached node from <code>oldPath</code> to <code>newPath</code>.
	 * Doing so will mark both the old and new parent dirty.
	 *
	 * @param oldPath The path of the node to be moved
	 * @param newPath The target path
	 * @return The moved node. Empty if this node has not been cached
	 */
	public Optional<CachedNode> move(CloudPath oldPath, CloudPath newPath) {
		var newParent = newPath.getParent();
		var node = getCachedNode(oldPath);
		node.ifPresent(n -> {
			delete(n);
			getCachedNode(newParent).ifPresent(p -> p.addChild(n));
		});
		markDirty(oldPath);
		markDirty(newParent);
		return node;
	}

}
