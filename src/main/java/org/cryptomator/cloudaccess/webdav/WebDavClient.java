package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.InsufficientStorageException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class WebDavClient {

	private final WebDavCompatibleHttpClient httpClient;
	private final URL baseUrl;
	private final int HTTP_INSUFFICIENT_STORAGE = 507;
	private final Comparator<PropfindEntryData> ASCENDING_BY_DEPTH = Comparator.comparingInt(PropfindEntryData::getDepth);

	WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential) {
		this.httpClient = httpClient;
		this.baseUrl = webDavCredential.getBaseUrl();
	}

	CloudItemList list(final CloudPath folder) throws CloudProviderException {
		return list(folder, PROPFIND_DEPTH.ONE);
	}

	CloudItemList listExhaustively(CloudPath folder) throws CloudProviderException {
		return list(folder, PROPFIND_DEPTH.INFINITY);
	}

	private CloudItemList list(final CloudPath folder, final PROPFIND_DEPTH propfind_depth) throws CloudProviderException {
		try (final var response = executePropfindRequest(folder, propfind_depth)) {
			checkExecutionSucceeded(response.code());

			final var nodes = getEntriesFromResponse(response);

			return processDirList(nodes);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemMetadata itemMetadata(final CloudPath path) throws CloudProviderException {
		try (final var response = executePropfindRequest(path, PROPFIND_DEPTH.ZERO)) {
			checkExecutionSucceeded(response.code());

			final var nodes = getEntriesFromResponse(response);

			return processGet(nodes);
		} catch (IOException | SAXException e) {
			throw new CloudProviderException(e);
		}
	}

	private Response executePropfindRequest(final CloudPath path, final PROPFIND_DEPTH propfind_depth) throws IOException {
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
				.header("DEPTH", propfind_depth.value) //
				.header("Content-Type", "text/xml");

		return httpClient.execute(builder);
	}

	private List<PropfindEntryData> getEntriesFromResponse(final Response response) throws IOException, SAXException {
		try (final var responseBody = response.body()) {
			return new PropfindResponseParser().parse(responseBody.byteStream());
		}
	}

	private CloudItemMetadata processGet(final List<PropfindEntryData> entryData) {
		entryData.sort(ASCENDING_BY_DEPTH);
		return entryData.size() >= 1 ? entryData.get(0).toCloudItem() : null;
	}

	private CloudItemList processDirList(final List<PropfindEntryData> entryData) {
		var result = new CloudItemList(new ArrayList<>());

		if (entryData.isEmpty()) {
			return result;
		}

		entryData.sort(ASCENDING_BY_DEPTH);
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		for (PropfindEntryData childEntry : entryData.subList(1, entryData.size())) {
			result = result.add(List.of(childEntry.toCloudItem()));
		}
		return result;
	}

	CloudPath move(final CloudPath from, final CloudPath to, boolean replace) throws CloudProviderException {
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
		final var getRequest = new Request.Builder() //
				.get() //
				.url(absoluteURLFrom(path));
		return read(getRequest, progressListener);
	}

	InputStream read(final CloudPath path, final long offset, final long count, final ProgressListener progressListener) throws CloudProviderException {
		final var getRequest = new Request.Builder() //
				.header("Range", String.format("bytes=%d-%d", offset, offset + count - 1))
				.get() //
				.url(absoluteURLFrom(path));
		return read(getRequest, progressListener);
	}

	private InputStream read(final Request.Builder getRequest, final ProgressListener progressListener) throws CloudProviderException {
		try {
			final var response = httpClient.execute(getRequest);
			final var countingBody = new ProgressResponseWrapper(response.body(), progressListener);
			checkExecutionSucceeded(response.code());
			return countingBody.byteStream();
		} catch (IOException e) {
			throw new CloudProviderException(e);
		}
	}

	CloudItemMetadata write(final CloudPath file, final boolean replace, final InputStream data, final ProgressListener progressListener) throws CloudProviderException {
		if (!replace && exists(file)) {
			throw new AlreadyExistsException("CloudNode already exists and replace is false");
		}

		final var countingBody = new ProgressRequestWrapper(InputStreamRequestBody.from(data), progressListener);
		final var requestBuilder = new Request.Builder()
				.url(absoluteURLFrom(file))
				.put(countingBody);

		try (final var response = httpClient.execute(requestBuilder)) {
			checkExecutionSucceeded(response.code());
			return itemMetadata(file);
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
		final var optionsRequest = new Request.Builder()
				.method("OPTIONS", null)
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

	private enum PROPFIND_DEPTH {
		ZERO("0"),
		ONE("1"),
		INFINITY("infinity");

		private final String value;

		PROPFIND_DEPTH(final String value) {
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