package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Splitter;
import com.google.common.collect.Streams;
import org.cryptomator.cloudaccess.CachedNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

class PropfindEntryItemData implements CachedNode.Cachable<PropfindEntryItemData> {

	private final String path;
	private final boolean collection;
	private final Optional<Instant> lastModified;
	private final Optional<Long> size;
	private final String eTag;

	private PropfindEntryItemData(Builder builder) {
		this.path = builder.path;
		this.collection = builder.collection;
		this.lastModified = builder.lastModified;
		this.size = builder.size;
		this.eTag = builder.eTag;
	}

	public Optional<Instant> getLastModified() {
		return lastModified;
	}

	public String getPath() {
		return path;
	}

	public Optional<Long> getSize() {
		return size;
	}

	public boolean isCollection() {
		return collection;
	}

	public long getDepth() {
		return Splitter.on("/").omitEmptyStrings().splitToStream(path).count();
	}

	public String getName() {
		return Streams.findLast(Splitter.on("/").omitEmptyStrings().splitToStream(path)).orElse("");
	}

	public String getETag() {
		return eTag;
	}

	@Override
	public boolean isSameVersion(PropfindEntryItemData other) {
		return Objects.equals(eTag, other.eTag);
	}

	@Override
	public String toString() {
		return "PropfindEntryItemData{"
				+ "collection=" + collection
				+ ", lastModified=" + lastModified
				+ ", size=" + size
				+ ", eTag='" + eTag + '\''
				+ '}';
	}

	/**
	 * Creates a copy of this instance with the new path
	 *
	 * @param newPath The path for the copy
	 * @return The copy
	 */
	public PropfindEntryItemData withPath(String newPath) {
		return new PropfindEntryItemData.Builder() //
				.withCollection(isCollection()) //
				.withEtag(getETag()) //
				.withSize(getSize()) //
				.withLastModified(getLastModified()) //
				.withPath(newPath) //
				.build();
	}

	static class Builder {

		private static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+://[^/]+/(.*)$");

		private String path;
		private boolean collection = true;
		private Optional<Instant> lastModified = Optional.empty();
		private Optional<Long> size = Optional.empty();
		private String eTag;

		Builder withLastModified(final Optional<Instant> lastModified) {
			this.lastModified = lastModified;
			return this;
		}

		Builder withPath(final String pathOrUri) {
			this.path = extractPath(pathOrUri);
			return this;
		}

		private String extractPath(final String pathOrUri) {
			final var matcher = URI_PATTERN.matcher(pathOrUri);
			if (matcher.matches()) {
				return urlDecode(matcher.group(1));
			} else if (!pathOrUri.startsWith("/")) {
				return urlDecode("/" + pathOrUri);
			} else {
				return urlDecode(pathOrUri);
			}
		}

		private String urlDecode(final String value) {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}

		Builder withSize(final Optional<Long> size) {
			this.size = size;
			return this;
		}

		Builder withCollection(final boolean collection) {
			this.collection = collection;
			return this;
		}

		Builder withEtag(String etag) {
			this.eTag = etag;
			return this;
		}

		PropfindEntryItemData build() {
			return new PropfindEntryItemData(this);
		}
	}

	static class AscendingByDepthComparator implements Comparator<PropfindEntryItemData> {
		@Override
		public int compare(PropfindEntryItemData p1, PropfindEntryItemData p2) {
			return (int) (p1.getDepth() - p2.getDepth());
		}
	}
}
