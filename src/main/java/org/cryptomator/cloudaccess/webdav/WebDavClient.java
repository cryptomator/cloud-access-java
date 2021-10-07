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
import org.cryptomator.cloudaccess.api.exceptions.CloudTimeoutException;
import org.cryptomator.cloudaccess.api.exceptions.InsufficientStorageException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.ParentFolderDoesNotExistException;
import org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WebDavClient {

	private static final Logger LOG = LoggerFactory.getLogger(WebDavClient.class);
	private static final String NEXTCLOUD_WEBDAV_PATH = "/remote.php/webdav";
	private final WebDavCompatibleHttpClient httpClient;
	private final URL baseUrl;
	private final int HTTP_INSUFFICIENT_STORAGE = 507;
	private CachedNode root = CachedNode.detached("");

	WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential) {
		this.httpClient = httpClient;
		this.baseUrl = webDavCredential.getBaseUrl();
	}

	private void initialListInfinitDepthRequest() {
		try (final var response = executePropfindRequest(CloudPath.of("/"), PropfindDepth.INFINITY)) {
			checkPropfindExecutionSucceeded(response.code());

			root = CachedNode.detached("");

			for (PropfindEntryItemData propfindEntryItemData : getEntriesFromResponse(response)) {
				var pathSegments = propfindEntryItemData.getPath().split("/");

				var parent = root;

				for (String pathSegment : pathSegments) {
					var optionalChild = parent.getChildren().stream().filter(child -> child.getName().equals(pathSegment)).findFirst();
					if (optionalChild.isPresent()) {
						parent = optionalChild.get();
					} else {
						var child = CachedNode.detached(pathSegment);
						parent.addChild(child);
						parent = child;
					}
				}

				// set data to leaf
				parent.setData(propfindEntryItemData);
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemMetadata itemMetadata(CloudPath path) throws CloudProviderException {
		LOG.trace("itemMetadata {}", path);
		var fullPath = CloudPath.of(NEXTCLOUD_WEBDAV_PATH + path.toAbsolutePath());
		return toCloudItem(getItemFromCache(fullPath).getData(PropfindEntryItemData.class), path);
	}

	private CachedNode getItemFromCache(CloudPath fullCloudPath) {
		var parent = root;
		for (String pathSegment : fullCloudPath.toAbsolutePath().toString().split("/")) {
			var optionalChild = parent.getChildren().stream().filter(child -> child.getName().equals(pathSegment)).findFirst();
			if (optionalChild.isPresent()) {
				parent = optionalChild.get();
			} else {
				throw new NotFoundException();
			}
		}
		return parent;
	}

	Quota quota(final CloudPath folder) throws CloudProviderException {
		LOG.trace("quota {}", folder);
		final var body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" //
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
			checkPropfindExecutionSucceeded(response.code());

			try (final var responseBody = response.body()) {
				return new PropfindResponseParser().parseQuta(responseBody.byteStream());
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemList list(final CloudPath folder) throws CloudProviderException {
		LOG.trace("list {}", folder);
		var fullPath = CloudPath.of(NEXTCLOUD_WEBDAV_PATH + folder.toAbsolutePath());
		var items = getItemFromCache(fullPath)
				.getChildren()
				.stream()
				.map(c -> c.getData(PropfindEntryItemData.class)).map(node -> toCloudItem(node, folder))
				.collect(Collectors.toList());
		return new CloudItemList(items);
	}

	private void checkPropfindExecutionSucceeded(int responseCode) {
		switch (responseCode) {
			case HttpURLConnection.HTTP_UNAUTHORIZED:
				throw new UnauthorizedException();
			case HttpURLConnection.HTTP_FORBIDDEN:
				throw new ForbiddenException();
			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new NotFoundException();
		}

		if (responseCode < 199 || responseCode > 300) {
			throw new CloudProviderException("Response code isn't between 200 and 300: " + responseCode);
		}
	}

	private Response executePropfindRequest(final CloudPath path, final PropfindDepth propfindDepth) throws IOException {
		final var body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" //
				+ "<d:propfind xmlns:d=\"DAV:\">\n" //
				+ "<d:prop>\n" //
				+ "<d:resourcetype />\n" //
				+ "<d:getcontentlength />\n" //
				+ "<d:getlastmodified />\n" //
				+ "<d:getetag />\n" //
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
			if (response.isSuccessful()) {
				return to;
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					case HttpURLConnection.HTTP_NOT_FOUND:
						throw new NotFoundException();
					case HttpURLConnection.HTTP_CONFLICT:
						throw new ParentFolderDoesNotExistException();
					case HttpURLConnection.HTTP_PRECON_FAILED:
						throw new AlreadyExistsException(absoluteURLFrom(to).toExternalForm());
					case HTTP_INSUFFICIENT_STORAGE:
						throw new InsufficientStorageException();
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
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
			if (response.isSuccessful()) {
				success = true;
				return countingBody.byteStream();
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					case HttpURLConnection.HTTP_NOT_FOUND:
						throw new NotFoundException();
					case 416: // UNSATISFIABLE_RANGE
						return new ByteArrayInputStream(new byte[0]);
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
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
			if (!response.isSuccessful()) {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					case HttpURLConnection.HTTP_BAD_METHOD:
						throw new TypeMismatchException();
					case HttpURLConnection.HTTP_CONFLICT: // fall through
					case HttpURLConnection.HTTP_NOT_FOUND: // necessary due to a bug in Nextcloud, see https://github.com/nextcloud/server/issues/23519
						throw new ParentFolderDoesNotExistException();
					case HTTP_INSUFFICIENT_STORAGE:
						throw new InsufficientStorageException();
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
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
		final var builder = new Request.Builder() //
				.method("MKCOL", null) //
				.url(absoluteURLFrom(path));

		try (final var response = httpClient.execute(builder)) {
			if (response.isSuccessful()) {
				return path;
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					case HttpURLConnection.HTTP_BAD_METHOD:
						throw new AlreadyExistsException(String.format("Folder %s already exists", path));
					case HttpURLConnection.HTTP_CONFLICT:
						throw new ParentFolderDoesNotExistException();
					case HTTP_INSUFFICIENT_STORAGE:
						throw new InsufficientStorageException();
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
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
			if (!response.isSuccessful()) {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					case HttpURLConnection.HTTP_NOT_FOUND:
						throw new NotFoundException(String.format("Node %s doesn't exists", path.toString()));
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
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
			if (response.isSuccessful()) {
				final var containsDavHeader = response.headers().names().contains("DAV");
				if (!containsDavHeader) {
					throw new ServerNotWebdavCompatibleException();
				}
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						throw new UnauthorizedException();
					case HttpURLConnection.HTTP_FORBIDDEN:
						throw new ForbiddenException();
					default:
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	void tryAuthenticatedRequest() throws UnauthorizedException {
		LOG.trace("tryAuthenticatedRequest");
		//itemMetadata(CloudPath.of("/"));
	}

	// visible for testing
	URL absoluteURLFrom(final CloudPath relativePath) {
		var basePath = CloudPath.of(baseUrl.getPath()).toAbsolutePath();
		var fullPath = IntStream.range(0, relativePath.getNameCount()).mapToObj(relativePath::getName).reduce(basePath, CloudPath::resolve);
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

		static WebDavClient createAuthenticatedWebDavClient(final WebDavCredential webDavCredential, WebDavProviderConfig config) throws ServerNotWebdavCompatibleException, UnauthorizedException {
			final var webDavClient = new WebDavClient(new WebDavCompatibleHttpClient(webDavCredential, config), webDavCredential);

			webDavClient.checkServerCompatibility();
			webDavClient.tryAuthenticatedRequest();
			webDavClient.initialListInfinitDepthRequest();

			return webDavClient;
		}
	}

}