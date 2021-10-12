package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CachedPropfindEntryProvider {

	private final NodeCache cache;

	public CachedPropfindEntryProvider() {
		cache = new NodeCache();
	}

	PropfindEntryItemData itemMetadata(String path, Function<String, PropfindEntryItemData> loader) {
		var cachedNode = cache.getCachedNode(path);
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get().getData(PropfindEntryItemData.class);
		} else {
			var loaded = loader.apply(path);
			// TODO add loaded to cache
			return loaded;
		}
	}

	List<PropfindEntryItemData> list(String path, Function<String, List<PropfindEntryItemData>> loader) throws CloudProviderException {
		var cachedNode = cache.getCachedNode(path);
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get()
					.getChildren()
					.stream()
					.map(c -> c.getData(PropfindEntryItemData.class))
					.collect(Collectors.toList());
		} else {
			var loaded = loader.apply(path);
			// TODO add loaded to cache
			return loaded;
		}
	}

	void move(String from, String to) {
		cache.move(from, to);
	}

	void write(String path) {
		cache.markDirty(path);
	}

	void createFolder(String path) {
		cache.markDirty(path);
	}

	void delete(String path) {
		cache.delete(path);
	}
}
