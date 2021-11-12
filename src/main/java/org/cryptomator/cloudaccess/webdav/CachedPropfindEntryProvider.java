package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
import org.cryptomator.cloudaccess.CachedNode;
import org.cryptomator.cloudaccess.NodeCache;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

class CachedPropfindEntryProvider {

	private final NodeCache cache;

	CachedPropfindEntryProvider() {
		this(new NodeCache());
	}

	// visible for testing
	CachedPropfindEntryProvider(NodeCache cache) {
		this.cache = cache;
	}

	public PropfindEntryItemData itemMetadata(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> parentLoader, Function<CloudPath, List<PropfindEntryItemData>> pathLoader) {
		var cachedNode = cache.getCachedNode(path);
		Optional<CachedNode> cachedParent = path.getParent() != null ? cache.getCachedNode(path.getParent()) : Optional.empty();
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get().getData(PropfindEntryItemData.class);
		} else if (cachedNode.isEmpty() && cachedParent.isPresent() && !cachedParent.get().isDirty() && cachedParent.get().isChildrenFetched()) {
			// node is not found, despite parent being up-to-date -> node does not exist
			throw new NotFoundException(path.toString());
		} else if (path.getParent() == null || (cachedParent.isPresent() && !cachedParent.get().isDirty() && cachedParent.get().isChildrenFetched())) {
			// parent is available, request path
			return loadPath(path, pathLoader);
		} else {
			/* parent is not available, request parent to avoid listings against `/foo/bar/baz` while `/foo/bar/` has not yet been queried which can lead to spam requests
			as `isChildrenFetched` is in this case not set in the parent and would also not be set using a PROPFIND on the child. Therefore listing on the parent. */
			return loadParent(path, parentLoader);
		}
	}

	private PropfindEntryItemData loadPath(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> pathLoader) {
		try {
			var entries = getPropfindEntryItemData(path, pathLoader);
			Preconditions.checkArgument(entries.size() >= 1, "got not more than one item");
			return entries.get(0);
		} catch (NotFoundException e) {
			cache.deleteAndMarkDirtyIfPresent(path);
			throw e;
		}
	}

	private PropfindEntryItemData loadParent(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> parentLoader) {
		try {
			var parentPath = path.getParent() != null ? path.getParent() : CloudPath.of("/");
			var entries = getPropfindEntryItemData(parentPath, parentLoader);
			var actualQueriedNode = entries.stream().filter(entry -> entry.getName().equals(path.getFileName().toString())).collect(Collectors.toList());
			if (actualQueriedNode.isEmpty()) {
				throw new NotFoundException(path.toString());
			}
			return actualQueriedNode.get(0);
		} catch (NotFoundException e) {
			cache.deleteAndMarkDirtyIfPresent(path);
			throw e;
		}
	}

	public List<PropfindEntryItemData> list(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> loader) throws CloudProviderException {
		var cachedNode = cache.getCachedNode(path);
		Optional<CachedNode> cachedParent = path.getParent() != null ? cache.getCachedNode(path.getParent()) : Optional.empty();
		if (cachedNode.isPresent() && !cachedNode.get().isDirty() && cachedNode.get().isChildrenFetched()) {
			return cachedNode.get()
					.getChildren()
					.stream()
					.map(c -> c.getData(PropfindEntryItemData.class))
					.collect(Collectors.toList());
		} else if (cachedNode.isEmpty() && cachedParent.isPresent() && !cachedParent.get().isDirty() && cachedParent.get().isChildrenFetched()) {
			// node is not found, despite parent being up-to-date -> node does not exist
			throw new NotFoundException(path.toString());
		} else {
			// skip(1) to remove the parent from the list
			return getPropfindEntryItemData(path, loader).stream().skip(1).collect(Collectors.toList());
		}
	}

	private List<PropfindEntryItemData> getPropfindEntryItemData(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> loader) {
		var loaded = loader.apply(path);
		loaded.sort(new PropfindEntryItemData.AscendingByDepthComparator());
		if (loaded.size() > 0) {
			var parent = loaded.get(0);
			cache.getOrCreateCachedNode(path).update(parent);
			cache.getOrCreateCachedNode(path).setChildrenFetched();
		}
		var children = loaded.stream().skip(1).collect(Collectors.toList());
		for (var data : children) {
			var p = path.resolve(data.getName());
			cache.getOrCreateCachedNode(p).update(data);
		}
		return loaded;
	}

	public void move(CloudPath from, CloudPath to) {
		var moved = cache.move(from, to);
		moved.ifPresent(node -> {
			var oldMetaData = node.getData(PropfindEntryItemData.class);
			var newMetadata = oldMetaData.withPath(to.toString());
			node.update(newMetadata);
		});
	}

	public void write(CloudPath path) {
		cache.markDirty(path);
	}

	public void createFolder(CloudPath path) {
		cache.markDirty(path);
	}

	public void delete(CloudPath path) {
		cache.delete(path);
	}
}
