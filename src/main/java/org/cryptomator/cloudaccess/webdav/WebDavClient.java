package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WebDavClient {

	private static final Logger LOG = LoggerFactory.getLogger(WebDavClient.class);
	private final WebDavCompatibleHttpClient httpClient;
	private final URL baseUrl;
	private final int HTTP_INSUFFICIENT_STORAGE = 507;

	private Optional<CachedPropfindEntryProvider> cachedPropfindEntryProvider = Optional.empty();

	WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential) {
		this.httpClient = httpClient;
		this.baseUrl = webDavCredential.getBaseUrl();
	}

	WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential, final CachedPropfindEntryProvider cachedPropfindEntryProvider) {
		this.httpClient = httpClient;
		this.baseUrl = webDavCredential.getBaseUrl();
		this.cachedPropfindEntryProvider = Optional.of(cachedPropfindEntryProvider);
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

		final var quotaRequest = new Request.Builder() //
				.method("PROPFIND", RequestBody.create(body, MediaType.parse(body))) //
				.url(absoluteURLFrom(folder)) //
				.header("Depth", PropfindDepth.ZERO.value) //
				.header("Content-Type", "text/xml");

		try (final var response = httpClient.execute(quotaRequest)) {
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

	CloudItemMetadata itemMetadata(CloudPath path) throws CloudProviderException {
		LOG.trace("itemMetadata {}", path);
		var parentPath = path.getParent() != null ? path.getParent() : CloudPath.of("/");
		var propfindEntryItemData = cachedPropfindEntryProvider
				.map(cachedProvider -> cachedProvider.itemMetadata(path, unused -> loadPropfindItems(parentPath), this::loadPropfindItems))
				.orElseGet(() -> loadPropfindItem(path));
		return toCloudItem(propfindEntryItemData, parentPath);
	}

	private PropfindEntryItemData loadPropfindItem(CloudPath path) {
		try (final var response = executePropfindRequest(path, PropfindDepth.ZERO)) {
			checkPropfindExecutionSucceeded(response.code());
			var entries = getEntriesFromResponse(response);
			Preconditions.checkArgument(entries.size() == 1, "got not exactally one item");
			return entries.get(0);
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemList list(final CloudPath folder) throws CloudProviderException {
		LOG.trace("list {}", folder);
		var propfindEntryItemDataList = cachedPropfindEntryProvider
				.map(cachedProvider -> cachedProvider.list(folder, this::loadPropfindItems))
				.orElseGet(() -> {
					var loaded = loadPropfindItems(folder);
					// skip parent folder as it is in the result as well
					loaded.sort(new PropfindEntryItemData.AscendingByDepthComparator());
					return loaded.stream().skip(1).collect(Collectors.toList());
				});
		return new CloudItemList(propfindEntryItemDataList.stream().map(node -> toCloudItem(node, folder)).collect(Collectors.toList()));
	}

	private List<PropfindEntryItemData> loadPropfindItems(CloudPath path) {
		try (final var response = executePropfindRequest(path, PropfindDepth.ONE)) {
			checkPropfindExecutionSucceeded(response.code());
			return getEntriesFromResponse(response);
		} catch (InterruptedIOException e) {
			throw new CloudTimeoutException(e);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
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

		final var propfindRequest = new Request.Builder() //
				.method("PROPFIND", RequestBody.create(body, MediaType.parse(body))) //
				.url(absoluteURLFrom(path)) //
				.header("Depth", propfindDepth.value) //
				.header("Content-Type", "text/xml");

		return httpClient.execute(propfindRequest);
	}

	private List<PropfindEntryItemData> getEntriesFromResponse(final Response response) throws IOException, SAXException {
		try (final var responseBody = response.body()) {
			return new PropfindResponseParser().parseItemData(responseBody.byteStream());
		}
	}

	private CloudItemMetadata toCloudItem(final PropfindEntryItemData data, final CloudPath parentPath) {
		if (data.isCollection()) {
			return new CloudItemMetadata(data.getName(), parentPath.resolve(data.getName()), CloudItemType.FOLDER);
		} else {
			return new CloudItemMetadata(data.getName(), parentPath.resolve(data.getName()), CloudItemType.FILE, data.getLastModified(), data.getSize());
		}
	}

	CloudPath move(final CloudPath from, final CloudPath to, boolean replace) throws CloudProviderException {
		LOG.trace("move {} to {} (replace: {})", from, to, replace ? "true" : "false");
		final var moveRequest = new Request.Builder() //
				.method("MOVE", null) //
				.url(absoluteURLFrom(from)) //
				.header("Destination", absoluteURLFrom(to).toExternalForm()) //
				.header("Content-Type", "text/xml") //
				.header("Depth", "infinity");

		if (!replace) {
			moveRequest.header("Overwrite", "F");
		}
		/*if (cachingSupported()) {
			moveRequest.header("If-Match", String.format("\"%s\"", "*"));
		}*/

		try (final var response = httpClient.execute(moveRequest)) {
			if (response.isSuccessful()) {
				cachedPropfindEntryProvider.ifPresent(cachedProvider -> cachedProvider.move(from, to));
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
						// FIXME has now different cases
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
		final var writeRequest = new Request.Builder() //
				.url(absoluteURLFrom(file)) //
				.put(countingBody);

		/*if (cachingSupported()) {
			writeRequest.header("If-Match", String.format("\"%s\"", "*"));
		}*/

		lastModified.ifPresent(instant -> writeRequest.addHeader("X-OC-Mtime", String.valueOf(instant.getEpochSecond())));

		try (final var response = httpClient.execute(writeRequest)) {
			if (response.isSuccessful()) {
				var eTag = Optional.ofNullable(response.header("ETag"));
				cachedPropfindEntryProvider.ifPresent(cachedProvider -> cachedProvider.write(file, size, lastModified, eTag));
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_PRECON_FAILED:
						// TODO
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
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
		final var createFolderRequest = new Request.Builder() //
				.method("MKCOL", null) //
				.url(absoluteURLFrom(path));

		/*if (cachingSupported()) {
			createFolderRequest.header("If-Match", String.format("\"%s\"", "*"));
		}*/

		try (final var response = httpClient.execute(createFolderRequest)) {
			if (response.isSuccessful()) {
				cachedPropfindEntryProvider.ifPresent(cachedProvider -> cachedProvider.createFolder(path));
				return path;
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_PRECON_FAILED:
						// TODO
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
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
		final var deleteRequest = new Request.Builder() //
				.delete() //
				.url(absoluteURLFrom(path));

		/*if (cachingSupported()) {
			deleteRequest.header("If-Match", String.format("\"%s\"", "*"));
		}*/

		try (final var response = httpClient.execute(deleteRequest)) {
			if (response.isSuccessful()) {
				cachedPropfindEntryProvider.ifPresent(cachedProvider -> cachedProvider.delete(path));
			} else {
				switch (response.code()) {
					case HttpURLConnection.HTTP_PRECON_FAILED:
						// TODO
						throw new CloudProviderException("Response code isn't between 200 and 300: " + response.code());
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

	PropfindEntryItemData tryAuthenticatedRequest() throws UnauthorizedException {
		LOG.trace("tryAuthenticatedRequest");
		return loadPropfindItem(CloudPath.of("/"));
	}

	void canUseCaching(PropfindEntryItemData propfindEntryItemData, WebDavProviderConfig config) throws UnauthorizedException {
		LOG.trace("canUseCaching");
		if (propfindEntryItemData.getETag() != null) {
			Function<CloudPath, PropfindEntryItemData> rootPoller = this::loadPropfindItem;
			Function<CloudPath, List<PropfindEntryItemData>> cacheUpdater = this::loadPropfindItems;
			cachedPropfindEntryProvider = Optional.of(new CachedPropfindEntryProvider(config, rootPoller, cacheUpdater));
		}
	}

	private boolean cachingSupported() {
		return cachedPropfindEntryProvider.isPresent();
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
			var propfindEntryItemData = webDavClient.tryAuthenticatedRequest();
			webDavClient.canUseCaching(propfindEntryItemData, config);

			return webDavClient;
		}
	}

}