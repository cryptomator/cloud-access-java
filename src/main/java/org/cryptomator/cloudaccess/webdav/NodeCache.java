package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import java.util.Iterator;
import java.util.Optional;

class NodeCache {

	private static final char PATH_SEP = '/';

	private final CachedNode root = CachedNode.detached("");

	private Iterable<String> getPathElements(String path) {
		return Splitter.on(PATH_SEP).omitEmptyStrings().split(path);
	}

	/**
	 * Attempts to retrieve a cached node. An empty response may either be a cache miss or the node does not exist.
	 *
	 * @param path The path of the node
	 * @return Cached node or an empty response in case of non-existing or non-cached nodes.
	 */
	public Optional<CachedNode> getCachedNode(String path) {
		var pathElements = getPathElements(path).iterator();
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
	 * Marks every cached node along the given <code>path</code> as dirty.
	 * @param path The path of the node
	 */
	public void markDirty(String path) {
		var pathElements = getPathElements(path).iterator();
		markDirty(root, pathElements);
	}

	private void markDirty(CachedNode node, Iterator<String> remainingPathElements) {
		node.markDirty();
		if (remainingPathElements.hasNext()) {
			var nextNode = node.getChild(remainingPathElements.next());
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
	public void delete(String path) {
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
	 * @param oldPath
	 * @param newPath
	 */
	public void move(String oldPath, String newPath) {
		var newParent = getParent(newPath);
		var node = getCachedNode(oldPath);
		node.ifPresent(n -> {
			delete(n);
			getCachedNode(newParent).ifPresent(p -> p.addChild(n));
		});
		markDirty(oldPath);
		markDirty(newParent);
	}

	private String getParent(String path) {
		var idx = path.lastIndexOf(PATH_SEP);
		if (idx == -1) {
			return ""; // root
		} else {
			return path.substring(0, idx);
		}
	}

}
