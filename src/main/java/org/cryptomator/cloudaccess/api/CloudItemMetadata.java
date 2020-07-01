package org.cryptomator.cloudaccess.api;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class CloudItemMetadata {
	private final String name;
	private final Path path;
	private final CloudItemType itemType;
	private final Optional<Instant> lastModifiedDate;
	private final Optional<Long> size;

	public CloudItemMetadata(String name, Path path, CloudItemType itemType, Optional<Instant> lastModifiedDate, Optional<Long> size) {
		this.name = name;
		this.path = path;
		this.itemType = itemType;
		this.lastModifiedDate = lastModifiedDate;
		this.size = size;
	}

	public CloudItemMetadata(String name, Path path, CloudItemType itemType) {
		this(name, path, itemType, Optional.empty(), Optional.empty());
	}

	public String getName() {
		return name;
	}

	public Path getPath() {
		return path;
	}

	public CloudItemType getItemType() {
		return itemType;
	}

	public Optional<Instant> getLastModifiedDate() {
		return lastModifiedDate;
	}

	public Optional<Long> getSize() {
		return size;
	}
}