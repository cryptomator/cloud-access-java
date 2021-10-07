package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Splitter;

import java.util.Iterator;
import java.util.Optional;

class NodeCache {

	private final CachedNode root;

	private NodeCache() {
		this.root = CachedNode.detached("");
	}

	public Optional<CachedNode> getCachedNode(String path) {
		var pathElements = Splitter.on('/').split(path).iterator();
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

}
