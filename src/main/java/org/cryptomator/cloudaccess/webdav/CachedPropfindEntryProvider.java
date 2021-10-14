package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class CachedPropfindEntryProvider {

	private final NodeCache cache = new NodeCache();

	public PropfindEntryItemData itemMetadata(CloudPath path, Function<CloudPath, PropfindEntryItemData> loader) {
		var cachedNode = cache.getCachedNode(path);
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get().getData(PropfindEntryItemData.class);
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
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			// FIXME: this approach assumes that when a folder is non-dirty, all children are cached. Is this a safe assumption?
			return cachedNode.get()
					.getChildren()
					.stream()
					.map(c -> c.getData(PropfindEntryItemData.class))
					.collect(Collectors.toList());
		} else {
			var loaded = loader.apply(path);
			for (var data : loaded) {
				var p = CloudPath.of(data.getPath());
				cache.getOrCreateCachedNode(p).update(data);
			}
			return loaded;
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
