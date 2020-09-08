package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Splitter;
import com.google.common.collect.Streams;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

class PropfindEntryData {

	private static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+://[^/]+/(.*)$");

	private String path;

	private boolean collection = true;
	private Optional<Instant> lastModified = Optional.empty();
	private Optional<Long> size = Optional.empty();

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

	public Optional<Instant> getLastModified() {
		return lastModified;
	}

	void setLastModified(final Optional<Instant> lastModified) {
		this.lastModified = lastModified;
	}

	public String getPath() {
		return path;
	}

	void setPath(final String pathOrUri) {
		this.path = extractPath(pathOrUri);
	}

	public Optional<Long> getSize() {
		return size;
	}

	void setSize(final Optional<Long> size) {
		this.size = size;
	}

	public boolean isCollection() {
		return collection;
	}

	void setCollection(final boolean collection) {
		this.collection = collection;
	}

	public long getDepth() {
		return Splitter.on("/").omitEmptyStrings().splitToStream(path).count();
	}

	public String getName() {
		return Streams.findLast(Splitter.on("/").omitEmptyStrings().splitToStream(path)).orElse("");
	}
}
