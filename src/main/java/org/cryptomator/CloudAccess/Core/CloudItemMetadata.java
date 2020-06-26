package org.cryptomator.CloudAccess.Core;

import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

public class CloudItemMetadata {
	private final String name;
	private final Path path;
	private final CloudItemType itemType;
	private final Optional<Date> lastModifiedDate;
	private final Optional<Long> size;

	public CloudItemMetadata(final String name, final Path path, final CloudItemType itemType, final Optional<Date> lastModifiedDate, final Optional<Long> size) {
		this.name = name;
		this.path = path;
		this.itemType = itemType;
		this.lastModifiedDate = lastModifiedDate;
		this.size = size;
	}

	public CloudItemMetadata(final String name, final Path path, final CloudItemType itemType) {
		this.name = name;
		this.path = path;
		this.itemType = itemType;
		this.lastModifiedDate = Optional.empty();
		this.size = Optional.empty();
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

	public Optional<Date> getLastModifiedDate() {
		return lastModifiedDate;
	}

	public Optional<Long> getSize() {
		return size;
	}
}