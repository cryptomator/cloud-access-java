package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
import org.cryptomator.cloudaccess.CachedNode;
import org.cryptomator.cloudaccess.NodeCache;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

class CachedPropfindEntryProvider {

	private static final Logger LOG = LoggerFactory.getLogger(CachedPropfindEntryProvider.class);

	private final NodeCache cache;

	private Function<CloudPath, PropfindEntryItemData> rootPoller;
	private Function<CloudPath, List<PropfindEntryItemData>> cacheUpdater;

	CachedPropfindEntryProvider(Function<CloudPath, PropfindEntryItemData> rootPoller, Function<CloudPath, List<PropfindEntryItemData>> cacheUpdater) {
		this(new NodeCache());

		this.rootPoller = rootPoller;
		this.cacheUpdater = cacheUpdater;
	}

	// visible for testing
	CachedPropfindEntryProvider(NodeCache cache) {
		this.cache = cache;
	}

	public PropfindEntryItemData itemMetadata(CloudPath path, Function<CloudPath, List<PropfindEntryItemData>> parentLoader, Function<CloudPath, List<PropfindEntryItemData>> pathLoader) {
		var cachedNode = cache.getCachedNode(path);
		Optional<CachedNode> cachedParent = Optional.ofNullable(path.getParent()).flatMap(cache::getCachedNode);
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
		Optional<CachedNode> cachedParent = Optional.ofNullable(path.getParent()).flatMap(cache::getCachedNode);
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

	public void write(CloudPath path, long size, Optional<Instant> lastModified, Optional<String> eTag) {
		if (eTag.isPresent()) {
			var data = new PropfindEntryItemData.Builder()
					.withPath(path.toString())
					.withCollection(false)
					.withSize(Optional.of(size))
					.withLastModified(lastModified)
					.withEtag(eTag.get())
					.build();
			cache.getOrCreateCachedNode(path).update(data);
			cache.markDirty(path.getParent());
		} else {
			cache.markDirty(path);
		}
	}

	public void createFolder(CloudPath path) {
		cache.markDirty(path);
	}

	public void delete(CloudPath path) {
		cache.delete(path);
	}

	public void pollRemoteChanges() {
		LOG.trace("polling remote changes");
		var rootPath = CloudPath.of("/");
		var root = cache.getCachedNode(rootPath);
		if (root.isPresent()) {
			var rootItemData = rootPoller.apply(rootPath);
			var localData = root.get().getData(PropfindEntryItemData.class);
			if (localData == null || !rootItemData.isSameVersion(localData)) {
				root.get().update(rootItemData);
				updateChildren(rootPath);
			}
		}
	}

	private void updateChildren(CloudPath node) {
		LOG.trace("updateChildren {}", node);
		var cacheNode = cache.getCachedNode(node);
		if (cacheNode.isPresent()) {
			var localChildren = cacheNode.get().getChildren();
			var remoteChildren = cacheUpdater.apply(node);

			// delete parent
			remoteChildren.sort(new PropfindEntryItemData.AscendingByDepthComparator());
			if (remoteChildren.size() > 0) {
				remoteChildren.remove(0);
			}

			// if local exists but not remote --> remove including descendant ✓
			// if local exists but different or no ETAG --> update, check descendant up to unchaged or not exist in cache ✓
			// if local exists and same ETAG --> ignore further sub-tree ✓
			// if remote exists but not local --> create ✓

			for (CachedNode localChild : localChildren) {
				var remoteItemMetadata = remoteChildren.stream().filter(remote -> remote.getName().equals(localChild.getName())).findAny();
				if (remoteItemMetadata.isPresent()) {
					var localData = localChild.getData(PropfindEntryItemData.class);
					if (localData != null) {
						updateSubTreeIfVersionChanged(node, localData, remoteItemMetadata, localChild);
					} else {
						updateSubTreeIfNoDataCachedButIsFolder(node, localChild, remoteItemMetadata);
					}
				} else {
					cache.delete(node.resolve(localChild.getName()));
				}
			}

			addRemoteNodesIfNotYetCached(node, localChildren, remoteChildren);

			cacheNode.get().setChildrenFetched();
		}
	}

	private void updateSubTreeIfVersionChanged(CloudPath node, PropfindEntryItemData localData, Optional<PropfindEntryItemData> remoteItemMetadata, CachedNode localChild) {
		if (!localData.isSameVersion(remoteItemMetadata.get())) {
			cache.getCachedNode(node.resolve(localChild.getName())).get().update(remoteItemMetadata.get());
			if (localData.isCollection()) {
				updateChildren(node.resolve(localChild.getName()));
			}
		} // else branch does nothing, even if a subtree exists but ETag of the parent didn't change
	}

	private void updateSubTreeIfNoDataCachedButIsFolder(CloudPath node, CachedNode localChild, Optional<PropfindEntryItemData> remoteItemMetadata) {
		cache.getCachedNode(node.resolve(localChild.getName())).get().update(remoteItemMetadata.get());
		if (remoteItemMetadata.get().isCollection()) {
			updateChildren(node.resolve(localChild.getName()));
		}
	}

	private void addRemoteNodesIfNotYetCached(CloudPath node, Collection<CachedNode> localChildren, List<PropfindEntryItemData> remoteChildren) {
		for (PropfindEntryItemData remoteChild : remoteChildren) {
			var localNode = localChildren.stream().filter(local -> local.getName().equals(remoteChild.getName())).findAny();
			if (localNode.isEmpty()) {
				cache.getOrCreateCachedNode(node.resolve(remoteChild.getName())).update(remoteChild);
			}
		}
	}
}
