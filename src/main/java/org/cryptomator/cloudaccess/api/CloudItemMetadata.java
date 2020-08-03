package org.cryptomator.cloudaccess.api;

import com.google.common.base.Objects;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class CloudItemMetadata {
	private final String name;
	private final CloudPath path;
	private final CloudItemType itemType;
	private final Optional<Instant> lastModifiedDate;
	private final Optional<Long> size;

	public CloudItemMetadata(String name, CloudPath path, CloudItemType itemType, Optional<Instant> lastModifiedDate, Optional<Long> size) {
		this.name = name;
		this.path = path;
		this.itemType = itemType;
		this.lastModifiedDate = lastModifiedDate;
		this.size = size;
	}

	public CloudItemMetadata(String name, CloudPath path, CloudItemType itemType) {
		this(name, path, itemType, Optional.empty(), Optional.empty());
	}

	public String getName() {
		return name;
	}

	public CloudPath getPath() {
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
		return Objects.equal(this.name, that.name) &&
				Objects.equal(this.path, that.path) &&
				this.itemType == that.itemType &&
				Objects.equal(this.lastModifiedDate, that.lastModifiedDate) &&
				Objects.equal(this.size, that.size);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name, path, itemType, lastModifiedDate, size);
	}

	@Override
	public String toString() {
		return "CloudItemMetadata{itemType=" + itemType + ", path=" + path + ", name=" + name + '}';
	}
}