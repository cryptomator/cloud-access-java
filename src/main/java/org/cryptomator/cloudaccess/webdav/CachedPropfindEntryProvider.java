package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Splitter;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CachedPropfindEntryProvider {

	private static final char PATH_SEP = '/';
	private final NodeCache cache;

	public CachedPropfindEntryProvider() {
		cache = new NodeCache();
	}

	PropfindEntryItemData itemMetadata(CloudPath path, Function<CloudPath, PropfindEntryItemData> loader) {
		var cachedNode = cache.getCachedNode(path.toAbsolutePath().toString());
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get().getData(PropfindEntryItemData.class);
		} else {
			try {
				var loaded = loader.apply(path);
				addCachedNodeIncludingAncestors(path.toAbsolutePath().toString());
				cache.getCachedNode(path.toAbsolutePath().toString()).get().update(loaded); // FIXME
				return loaded;
			} catch (NotFoundException e) {
				if(cachedNode.isPresent()) {
					cache.delete(path.toAbsolutePath().toString());
				}
				throw e;
			}
		}
	}

	private Iterable<String> getPathElements(String path) {
		return Splitter.on(PATH_SEP).omitEmptyStrings().split(path);
	}

	private CachedNode addCachedNodeIncludingAncestors(String path) {
		var pathElements = getPathElements(path).iterator();
		return getOrCreateCachedNode(cache.getCachedNode("/").orElse(CachedNode.detached("")), pathElements, "");
	}

	private CachedNode getOrCreateCachedNode(CachedNode base, Iterator<String> remainingPathElements, String path) {
		if (base == null || !remainingPathElements.hasNext()) {
			return base;
		} else {
			var childName = remainingPathElements.next();
			var next = base.getChild(childName);
			// Create cache node if not existent
			if(next == null) {
				next = base.addChild(CachedNode.detached(childName));
				cache.getCachedNode(path).get().addChild(next); // FIXME should not fail but implement it better
			}
			return getOrCreateCachedNode(next, remainingPathElements, path+"/"+childName);
		}
	}

	List<PropfindEntryItemData> list(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> loader) throws CloudProviderException {
		var cachedNode = cache.getCachedNode(path.toAbsolutePath().toString());
		if (cachedNode.isPresent() && !cachedNode.get().isDirty()) {
			return cachedNode.get()
					.getChildren()
					.stream()
					.map(c -> c.getData(PropfindEntryItemData.class))
					.collect(Collectors.toList());
		} else {
			var loaded = loader.apply(path);

			addCachedNodeIncludingAncestors(path.toAbsolutePath().toString());
			// propfind response also responds with the queried folder, so lets update the cached value
			if(loaded.size() > 0) {
				cache.getCachedNode(path.toAbsolutePath().toString()).get().update(loaded.get(0)); // FIXME
			}
			// add all children
			for(PropfindEntryItemData propfindEntryItemData: loaded.stream().skip(1).collect(Collectors.toList())) {
				cache.getCachedNode(path.toAbsolutePath().toString()).get().addChild(CachedNode.detached(propfindEntryItemData.getName(), propfindEntryItemData)); // FIXME
			}

			return loaded;
		}
	}

	void move(CloudPath from, CloudPath to) {
		cache.move(from.toAbsolutePath().toString(), to.toAbsolutePath().toString());
	}

	void write(CloudPath path) {
		cache.markDirty(path.toAbsolutePath().toString());
	}

	void createFolder(CloudPath path) {
		cache.markDirty(path.toAbsolutePath().toString());
	}

	void delete(CloudPath path) {
		cache.delete(path.toAbsolutePath().toString());
	}
}
