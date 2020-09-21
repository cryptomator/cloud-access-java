package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.Quota;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.InsufficientStorageException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class WebDavClient {

	private static final Logger LOG = LoggerFactory.getLogger(WebDavClient.class);
	private static final Comparator<PropfindEntryItemData> ASCENDING_BY_DEPTH = Comparator.comparingLong(PropfindEntryItemData::getDepth);

	private final WebDavCompatibleHttpClient httpClient;
	private final URL baseUrl;
	private final int HTTP_INSUFFICIENT_STORAGE = 507;

	WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential) {
		this.httpClient = httpClient;
		this.baseUrl = webDavCredential.getBaseUrl();
	}

	CloudItemMetadata itemMetadata(final CloudPath path) throws CloudProviderException {
		LOG.trace("itemMetadata {}", path);
		try (final var response = executePropfindRequest(path, PropfindDepth.ZERO)) {
			checkExecutionSucceeded(response.code());

			final var nodes = getEntriesFromResponse(response);

			return processGet(nodes, path);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	Quota quota(final CloudPath folder) throws CloudProviderException {
		LOG.trace("quota {}", folder);
		final var body = "<?xml version=\"1.0\" ?>\n" //
				+ "<d:propfind xmlns:d=\"DAV:\">\n" //
				+ "<d:prop>\n" //
				+ "<d:quota-available-bytes/>\n" //
				+ "<d:quota-used-bytes/>\n" //
				+ "</d:prop>\n" //
				+ "</d:propfind>";

		final var builder = new Request.Builder() //
				.method("PROPFIND", RequestBody.create(body, MediaType.parse(body))) //
				.url(absoluteURLFrom(folder)) //
				.header("Depth", PropfindDepth.ZERO.value) //
				.header("Content-Type", "text/xml");

		try (final var response = httpClient.execute(builder)) {
			checkExecutionSucceeded(response.code());

			try (final var responseBody = response.body()) {
				return new PropfindResponseParser().parseQuta(responseBody.byteStream());
			}
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemList list(final CloudPath folder) throws CloudProviderException {
		LOG.trace("list {}", folder);
		try (final var response = executePropfindRequest(folder, PropfindDepth.ONE)) {
			checkExecutionSucceeded(response.code());

			final var nodes = getEntriesFromResponse(response);

			return processDirList(nodes, folder);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	private Response executePropfindRequest(final CloudPath path, final PropfindDepth propfindDepth) throws IOException {
		final var body = "<d:propfind xmlns:d=\"DAV:\">\n" //
				+ "<d:prop>\n" //
				+ "<d:resourcetype />\n" //
				+ "<d:getcontentlength />\n" //
				+ "<d:getlastmodified />\n" //
				+ "</d:prop>\n" //
				+ "</d:propfind>";

		final var builder = new Request.Builder() //
				.method("PROPFIND", RequestBody.create(body, MediaType.parse(body))) //
				.url(absoluteURLFrom(path)) //
				.header("Depth", propfindDepth.value) //
				.header("Content-Type", "text/xml");

		return httpClient.execute(builder);
	}

	private List<PropfindEntryItemData> getEntriesFromResponse(final Response response) throws IOException, SAXException {
		try (final var responseBody = response.body()) {
			return new PropfindResponseParser().parseItemData(responseBody.byteStream());
		}
	}

	private CloudItemMetadata processGet(final List<PropfindEntryItemData> entryData, final CloudPath path) {
		entryData.sort(ASCENDING_BY_DEPTH);
		return entryData.size() >= 1 ? toCloudItem(entryData.get(0), path) : null;
	}

	private CloudItemList processDirList(final List<PropfindEntryItemData> entryData, final CloudPath folder) {
		var result = new CloudItemList(new ArrayList<>());

		if (entryData.isEmpty()) {
			return result;
		}

		entryData.sort(ASCENDING_BY_DEPTH);
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		for (PropfindEntryItemData childEntry : entryData.subList(1, entryData.size())) {
			result = result.add(List.of(toCloudItem(childEntry, folder.resolve(childEntry.getName()))));
		}
		return result;
	}

	private CloudItemMetadata toCloudItem(final PropfindEntryItemData data, final CloudPath path) {
		if (data.isCollection()) {
			return new CloudItemMetadata(data.getName(), path, CloudItemType.FOLDER);
		} else {
			return new CloudItemMetadata(data.getName(), path, CloudItemType.FILE, data.getLastModified(), data.getSize());
		}
	}

	CloudPath move(final CloudPath from, final CloudPath to, boolean replace) throws CloudProviderException {
		LOG.trace("move {} to {} (replace: {})", from, to, replace ? "true" : "false");
		final var builder = new Request.Builder() //
				.method("MOVE", null) //
				.url(absoluteURLFrom(from)) //
				.header("Destination", absoluteURLFrom(to).toExternalForm()) //
				.header("Content-Type", "text/xml") //
				.header("Depth", "infinity");

		if (!replace) {
			builder.header("Overwrite", "F");
		}

		try (final var response = httpClient.execute(builder)) {
			if (response.code() == HttpURLConnection.HTTP_PRECON_FAILED) {
				throw new AlreadyExistsException(absoluteURLFrom(to).toExternalForm());
			}

			checkExecutionSucceeded(response.code());

			return to;
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	InputStream read(final CloudPath path, final ProgressListener progressListener) throws CloudProviderException {
		LOG.trace("read {}", path);
		final var getRequest = new Request.Builder() //
				.get() //
				.url(absoluteURLFrom(path));
		return read(getRequest, progressListener);
	}

	InputStream read(final CloudPath path, final long offset, final long count, final ProgressListener progressListener) throws CloudProviderException {
		LOG.trace("read {} (offset: {}, count: {})", path, offset, count);
		final var getRequest = new Request.Builder() //
				.header("Range", String.format("bytes=%d-%d", offset, offset + count - 1)) //
				.get() //
				.url(absoluteURLFrom(path));
		return read(getRequest, progressListener);
	}

	private InputStream read(final Request.Builder getRequest, final ProgressListener progressListener) throws CloudProviderException {
		Response response = null;
		boolean success = false;
		try {
			response = httpClient.execute(getRequest);
			final var countingBody = new ProgressResponseWrapper(response.body(), progressListener);

			final int UNSATISFIABLE_RANGE = 416;
			if (response.code() == UNSATISFIABLE_RANGE) {
				return new ByteArrayInputStream(new byte[0]);
			}

			checkExecutionSucceeded(response.code());
			success = true;
			return countingBody.byteStream();
		} catch (IOException e) {
			throw new CloudProviderException(e);
		} finally {
			if (response != null && !success) {
				response.close();
			}
		}
	}

	void write(final CloudPath file, final boolean replace, final InputStream data, final long size, final Optional<Instant> lastModified, final ProgressListener progressListener) throws CloudProviderException {
		LOG.trace("write {} (size: {}, lastModified: {}, replace: {})", file, size, lastModified, replace ? "true" : "false");
		if (!replace && exists(file)) {
			throw new AlreadyExistsException("CloudNode already exists and replace is false");
		}

		final var countingBody = new ProgressRequestWrapper(InputStreamRequestBody.from(data, size), progressListener);
		final var requestBuilder = new Request.Builder() //
				.url(absoluteURLFrom(file)) //
				.put(countingBody);

		lastModified.ifPresent(instant -> requestBuilder.addHeader("X-OC-Mtime", String.valueOf(instant.getEpochSecond())));

		try (final var response = httpClient.execute(requestBuilder)) {
			checkExecutionSucceeded(response.code());
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	private boolean exists(CloudPath path) throws CloudProviderException {
		try {
			return itemMetadata(path) != null;
		} catch (NotFoundException e) {
			return false;
		}
	}

	CloudPath createFolder(final CloudPath path) throws CloudProviderException {
		LOG.trace("createFolder {}", path);
		if (exists(path)) {
			throw new AlreadyExistsException(String.format("Folder %s already exists", path.toString()));
		}

		final var builder = new Request.Builder() //
				.method("MKCOL", null) //
				.url(absoluteURLFrom(path));

		try (final var response = httpClient.execute(builder)) {
			checkExecutionSucceeded(response.code());
			return path;
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	void delete(final CloudPath path) throws CloudProviderException {
		LOG.trace("delete {}", path);
		final var builder = new Request.Builder() //
				.delete() //
				.url(absoluteURLFrom(path));

		try (final var response = httpClient.execute(builder)) {
			checkExecutionSucceeded(response.code());
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	void checkServerCompatibility() throws ServerNotWebdavCompatibleException {
		LOG.trace("checkServerCompatibility");
		final var optionsRequest = new Request.Builder() //
				.method("OPTIONS", null) //
				.url(baseUrl);

		try (final var response = httpClient.execute(optionsRequest)) {
			checkExecutionSucceeded(response.code());
			final var containsDavHeader = response.headers().names().contains("DAV");
			if (!containsDavHeader) {
				throw new ServerNotWebdavCompatibleException();
			}
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	void tryAuthenticatedRequest() throws UnauthorizedException {
		LOG.trace("tryAuthenticatedRequest");
		try {
			itemMetadata(CloudPath.of("/"));
		} catch (Exception e) {
			if (e instanceof UnauthorizedException) {
				throw e;
			}
		}
	}

	private void checkExecutionSucceeded(final int status) throws CloudProviderException {
		switch (status) {
			case HttpURLConnection.HTTP_UNAUTHORIZED:
				throw new UnauthorizedException();
			case HttpURLConnection.HTTP_FORBIDDEN:
				throw new ForbiddenException();
			case HttpURLConnection.HTTP_NOT_FOUND: // fall through
			case HttpURLConnection.HTTP_CONFLICT: //
				throw new NotFoundException();
			case HTTP_INSUFFICIENT_STORAGE:
				throw new InsufficientStorageException();
		}

		if (status < 199 || status > 300) {
			throw new CloudProviderException("Response code isn't between 200 and 300: " + status);
		}
	}

	// visible for testing
	URL absoluteURLFrom(final CloudPath relativePath) {
		var basePath = CloudPath.of(baseUrl.getPath()).toAbsolutePath();
		var fullPath = IntStream.range(0, relativePath.getNameCount()).mapToObj(i -> relativePath.getName(i)).reduce(basePath, CloudPath::resolve);
		try {
			return new URL(baseUrl, fullPath.toString());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("The relative path contains invalid URL elements.");
		}
	}

	private enum PropfindDepth {
		ZERO("0"), //
		ONE("1"), //
		INFINITY("infinity");

		private final String value;

		PropfindDepth(final String value) {
			this.value = value;
		}
	}

	static class WebDavAuthenticator {

		static WebDavClient createAuthenticatedWebDavClient(final WebDavCredential webDavCredential) throws ServerNotWebdavCompatibleException, UnauthorizedException {
			final var webDavClient = new WebDavClient(new WebDavCompatibleHttpClient(webDavCredential), webDavCredential);

			webDavClient.checkServerCompatibility();
			webDavClient.tryAuthenticatedRequest();

			return webDavClient;
		}
	}

}