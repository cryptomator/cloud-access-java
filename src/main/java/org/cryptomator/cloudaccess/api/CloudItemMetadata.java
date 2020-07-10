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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CloudItemMetadata that = (CloudItemMetadata) o;

		if (!name.equals(that.name)) return false;
		if (!path.equals(that.path)) return false;
		if (itemType != that.itemType) return false;
		if (!lastModifiedDate.equals(that.lastModifiedDate)) return false;
		return size.equals(that.size);
	}

	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + path.hashCode();
		result = 31 * result + itemType.hashCode();
		result = 31 * result + lastModifiedDate.hashCode();
		result = 31 * result + size.hashCode();
		return result;
	}
}