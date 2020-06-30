package org.cryptomator.cloudaccess.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudItemList {

	private final List<CloudItemMetadata> items;
	private final Optional<String> nextPageToken;

	public CloudItemList(final List<CloudItemMetadata> items, final Optional<String> nextPageToken) {
		this.items = items;
		this.nextPageToken = nextPageToken;
	}

	public CloudItemList(final List<CloudItemMetadata> items) {
		this(items, Optional.empty());
	}

	public List<CloudItemMetadata> getItems() {
		return items;
	}

	public Optional<String> getNextPageToken() {
		return nextPageToken;
	}

	public CloudItemList add(final List<CloudItemMetadata> items, final Optional<String> nextPageToken) {
		final List<CloudItemMetadata> union = Stream.concat(this.items.stream(), items.stream()).collect(Collectors.toList());
		return new CloudItemList(union, nextPageToken);
	}
	
	public static CloudItemList empty() {
		return new CloudItemList(Collections.emptyList());
	}
}
