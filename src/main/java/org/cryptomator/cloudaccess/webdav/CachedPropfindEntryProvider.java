package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;

import java.util.List;
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

	public PropfindEntryItemData itemMetadata(CloudPath path, Function<CloudPath, PropfindEntryItemData> loader) {
		var cachedNode = cache.getCachedNode(path);
		var cachedParent = cache.getCachedNode(path.getParent());
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get().getData(PropfindEntryItemData.class);
		} else if (cachedParent.isPresent() && !cachedParent.get().isDirty()) {
			// node is not found, despite parent being up-to-date -> node does not exist
			throw new NotFoundException(path.toString());
		} else {
			try {
				var loaded = loader.apply(path);
				cache.getOrCreateCachedNode(path).update(loaded);
				return loaded;
			} catch (NotFoundException e) {
				cache.delete(path);
				throw e;
			}
		}
	}

	public List<PropfindEntryItemData> list(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> loader) throws CloudProviderException {
		var cachedNode = cache.getCachedNode(path);
		if (cachedNode.isPresent() && !cachedNode.get().isDirty() && cachedNode.get().isChildrenFetched()) {
			return cachedNode.get()
					.getChildren()
					.stream()
					.map(c -> c.getData(PropfindEntryItemData.class))
					.collect(Collectors.toList());
		} else {
			var loaded = loader.apply(path);
			loaded.sort(new PropfindEntryItemData.AscendingByDepthComparator());
			if(loaded.size() > 0) {
				var parent = loaded.get(0);
				cache.getOrCreateCachedNode(path).update(parent);
			}
			var children = loaded.stream().skip(1).collect(Collectors.toList());
			for (var data : children) {
				var p = path.resolve(data.getName());
				cache.getOrCreateCachedNode(p).update(data);
			}
			return children;
		}
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
