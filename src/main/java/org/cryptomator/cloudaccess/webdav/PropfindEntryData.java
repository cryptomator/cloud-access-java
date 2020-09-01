package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.cryptomator.cloudaccess.api.CloudItemType.FILE;
import static org.cryptomator.cloudaccess.api.CloudItemType.FOLDER;

class PropfindEntryData {
	private static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+://[^/]+/(.*)$");

	private CloudPath path;

	private boolean file = true;
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

	void setLastModified(final Optional<Instant> lastModified) {
		this.lastModified = lastModified;
	}

	public CloudPath getPath() {
		return path;
	}

	public void setPath(final String pathOrUri) {
		this.path = CloudPath.of(extractPath(pathOrUri));
	}

	public Optional<Long> getSize() {
		return size;
	}

	public void setSize(final Optional<Long> size) {
		this.size = size;
	}

	private boolean isFile() {
		return file;
	}

	public void setFile(final boolean file) {
		this.file = file;
	}

	public CloudItemMetadata toCloudItem() {
		if (isFile()) {
			return new CloudItemMetadata(getName(), path, FILE, lastModified, size);
		} else {
			return new CloudItemMetadata(getName(), path, FOLDER);
		}
	}

	private String urlDecode(final String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	int getDepth() {
		return path.getNameCount();
	}

	private String getName() {
		return path.getFileName().toString();
	}

}
