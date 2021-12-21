package org.cryptomator.cloudaccess.webdav;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WebDavCloudProviderTestIT {

	private final MockWebServer server;
	private final CloudProvider provider;
	private final URL baseUrl;
	private final Duration timeout = Duration.ofMillis(1000);

	private final CloudItemMetadata testFolderDocuments = new CloudItemMetadata("Documents", CloudPath.of("/Documents"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private final CloudItemMetadata testFileManual = new CloudItemMetadata("Nextcloud Manual.pdf", CloudPath.of("/Nextcloud Manual.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(6837751L));
	private final CloudItemMetadata testFileIntro = new CloudItemMetadata("Nextcloud intro.mp4", CloudPath.of("/Nextcloud intro.mp4"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(462413L));
	private final CloudItemMetadata testFilePng = new CloudItemMetadata("Nextcloud.png", CloudPath.of("/Nextcloud.png"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(37042L));
	private final CloudItemMetadata testFolderPhotos = new CloudItemMetadata("Photos", CloudPath.of("/Photos"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());

	private final String webDavRequestBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<d:propfind xmlns:d=\"DAV:\">\n<d:prop>\n<d:resourcetype />\n<d:getcontentlength />\n<d:getlastmodified />\n<d:getetag />\n</d:prop>\n</d:propfind>";

	public WebDavCloudProviderTestIT() throws IOException, InterruptedException {
		server = new MockWebServer();
		server.start();

		baseUrl = new URL("http", server.getHostName(), server.getPort(), "/cloud/remote.php/webdav");

		final var response = getInterceptedResponse("item-meta-data-response.xml");
		server.enqueue(response);
		server.enqueue(response);

		provider = Assertions.assertTimeoutPreemptively(timeout, () -> WebDavCloudProvider.from(WebDavCredential.from(baseUrl, "foo", "bar")));

		Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf")
	public void testItemMetadata() throws InterruptedException {
		server.enqueue(getInterceptedResponse("item-meta-data-response.xml"));

		final var itemMetadata = Assertions.assertTimeoutPreemptively(timeout, () -> provider.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf")).toCompletableFuture().join());
		Assertions.assertEquals(itemMetadata, testFileManual);

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PROPFIND", rq.getMethod());
		Assertions.assertEquals("1", rq.getHeader("DEPTH"));
		Assertions.assertEquals("/cloud/remote.php/webdav", rq.getPath());
		Assertions.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
	}

	@Test
	@DisplayName("list /")
	public void testList() throws InterruptedException {
		server.enqueue(getInterceptedResponse("directory-list-response.xml"));

		final var nodeList = Assertions.assertTimeoutPreemptively(timeout, () -> provider.list(CloudPath.of("/"), Optional.empty()).toCompletableFuture().join());

		final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos);

		Assertions.assertEquals(expectedList, nodeList.getItems());
		Assertions.assertTrue(nodeList.getNextPageToken().isEmpty());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PROPFIND", rq.getMethod());
		Assertions.assertEquals("1", rq.getHeader("DEPTH"));
		Assertions.assertEquals("/cloud/remote.php/webdav", rq.getPath());
		Assertions.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
	}

	@Test
	@DisplayName("read /Documents/About.txt (complete)")
	public void testRead() throws InterruptedException {
		server.enqueue(getInterceptedResponse("item-read-response.txt"));

		final var inputStream = Assertions.assertTimeoutPreemptively(timeout, () -> provider.read(CloudPath.of("/Documents/About.txt"), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());
		final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

		Assertions.assertEquals(content, load("item-read-response.txt"));

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("GET", rq.getMethod());
		Assertions.assertNull(rq.getHeader("Range"));
		Assertions.assertEquals("/cloud/remote.php/webdav/Documents/About.txt", rq.getPath());
	}

	@Test
	@DisplayName("read /Documents/About.txt (bytes 4-6)")
	public void testRandomAccessRead() throws InterruptedException {
		server.enqueue(getInterceptedResponse("item-partial-read-response.txt"));

		final var inputStream = Assertions.assertTimeoutPreemptively(timeout, () -> provider.read(CloudPath.of("/Documents/About.txt"), 4, 2, ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());
		final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

		Assertions.assertEquals(content, load("item-partial-read-response.txt"));

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("GET", rq.getMethod());
		Assertions.assertEquals("bytes=4-5", rq.getHeader("Range"));
		Assertions.assertEquals("/cloud/remote.php/webdav/Documents/About.txt", rq.getPath());
	}

	@Test
	@DisplayName("write to /foo.txt (non-existing)")
	public void testWriteToNewFile() throws InterruptedException, IOException {
		server.enqueue(getInterceptedResponse(404, ""));
		server.enqueue(getInterceptedResponse(201, ""));
		server.enqueue(getInterceptedResponse("item-write-response.xml"));

		final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

		Assertions.assertTimeoutPreemptively(timeout, () -> provider.write(CloudPath.of("/foo.txt"), false, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PROPFIND", rq.getMethod());
		Assertions.assertEquals("1", rq.getHeader("DEPTH"));
		Assertions.assertEquals("/cloud/remote.php/webdav", rq.getPath());
		Assertions.assertEquals(webDavRequestBody, rq.getBody().readUtf8());

		rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PUT", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
		Assertions.assertNull(rq.getHeader("X-OC-Mtime"));
	}

	@Test
	@DisplayName("write to /foo.txt (non-existing, replace)")
	public void testWriteToAndReplaceNewFile() throws InterruptedException, IOException {
		server.enqueue(getInterceptedResponse(201, ""));
		server.enqueue(getInterceptedResponse("item-write-response.xml"));

		final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

		Assertions.assertTimeoutPreemptively(timeout, () -> provider.write(CloudPath.of("/foo.txt"), true, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PUT", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
		Assertions.assertNull(rq.getHeader("X-OC-Mtime"));
	}

	@Test
	@DisplayName("write to /file (already existing)")
	public void testWriteToExistingFile() throws InterruptedException {
		server.enqueue(getInterceptedResponse("item-write-response.xml"));

		final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

		Assertions.assertThrows(AlreadyExistsException.class, () -> Assertions.assertTimeoutPreemptively(timeout, () -> //
				provider.write(CloudPath.of("/foo.txt"), false, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE) //
						.toCompletableFuture() //
						.join()));

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PROPFIND", rq.getMethod());
		Assertions.assertEquals("1", rq.getHeader("DEPTH"));
		Assertions.assertEquals("/cloud/remote.php/webdav", rq.getPath());
		Assertions.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
	}

	@Test
	@DisplayName("write to /foo.txt (replace existing)")
	public void testWriteToAndReplaceExistingFile() throws InterruptedException, IOException {
		server.enqueue(getInterceptedResponse("item-write-response.xml"));
		server.enqueue(getInterceptedResponse("item-write-response.xml"));

		final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

		Assertions.assertTimeoutPreemptively(timeout, () -> provider.write(CloudPath.of("/foo.txt"), true, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PUT", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
		Assertions.assertNull(rq.getHeader("X-OC-Mtime"));
	}

	@Test
	@DisplayName("write to /foo.txt (try to set mod date)")
	public void testWriteAndTrySetModDate() throws InterruptedException, IOException {
		server.enqueue(getInterceptedResponse(404, ""));
		server.enqueue(getInterceptedResponse(201, ""));
		server.enqueue(getInterceptedResponse("item-write-response.xml"));

		final var modDate = Instant.now().minus(Duration.ofDays(365));

		final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
		Assertions.assertTimeoutPreemptively(timeout, () -> provider.write(CloudPath.of("/foo.txt"), false, inputStream, inputStream.available(), Optional.of(modDate), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PROPFIND", rq.getMethod());
		Assertions.assertEquals("1", rq.getHeader("DEPTH"));
		Assertions.assertEquals("/cloud/remote.php/webdav", rq.getPath());
		Assertions.assertEquals(webDavRequestBody, rq.getBody().readUtf8());

		rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("PUT", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
		Assertions.assertEquals(String.valueOf(modDate.getEpochSecond()), rq.getHeader("X-OC-Mtime"));
	}

	@Test
	@DisplayName("create /foo")
	public void testCreateFolder() throws InterruptedException {
		server.enqueue(getInterceptedResponse());

		final var path = Assertions.assertTimeoutPreemptively(timeout, () -> provider.createFolder(CloudPath.of("/foo")).toCompletableFuture().join());

		Assertions.assertEquals(path, CloudPath.of("/foo"));

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("MKCOL", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
	}

	@Test
	@DisplayName("delete /foo.txt")
	public void testDeleteFile() throws InterruptedException {
		server.enqueue(getInterceptedResponse());

		Assertions.assertTimeoutPreemptively(timeout, () -> provider.deleteFile(CloudPath.of("/foo.txt")).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("DELETE", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
	}

	@Test
	@DisplayName("delete /foo (recursively)")
	public void testDeleteFolder() throws InterruptedException {
		server.enqueue(getInterceptedResponse());

		Assertions.assertTimeoutPreemptively(timeout, () -> provider.deleteFolder(CloudPath.of("/foo")).toCompletableFuture().join());

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("DELETE", rq.getMethod());
		Assertions.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
	}

	@Test
	@DisplayName("move /foo -> /bar (non-existing)")
	public void testMoveToNonExisting() throws InterruptedException {
		server.enqueue(getInterceptedResponse());

		final var targetPath = Assertions.assertTimeoutPreemptively(timeout, () -> provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false).toCompletableFuture().join());

		Assertions.assertEquals(CloudPath.of("/bar"), targetPath);

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("MOVE", rq.getMethod());
		Assertions.assertEquals("infinity", rq.getHeader("Depth"));
		Assertions.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
		Assertions.assertEquals("F", rq.getHeader("Overwrite"));
		Assertions.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
	}

	@Test
	@DisplayName("move /foo -> /bar (already exists)")
	public void testMoveToExisting() throws InterruptedException {
		server.enqueue(getInterceptedResponse(412, "item-move-exists-no-replace.xml"));

		Assertions.assertThrows(AlreadyExistsException.class, () -> Assertions.assertTimeoutPreemptively(timeout, () -> provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false).toCompletableFuture().join()));

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("MOVE", rq.getMethod());
		Assertions.assertEquals("infinity", rq.getHeader("Depth"));
		Assertions.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
		Assertions.assertEquals("F", rq.getHeader("Overwrite"));
		Assertions.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
	}

	@Test
	@DisplayName("move /foo -> /bar (replace existing)")
	public void testMoveToAndReplaceExisting() throws InterruptedException {
		server.enqueue(getInterceptedResponse(204, ""));

		final var targetPath = Assertions.assertTimeoutPreemptively(timeout, () -> provider.move(CloudPath.of("/foo"), CloudPath.of("/bar"), true).toCompletableFuture().join());

		Assertions.assertEquals(CloudPath.of("/bar"), targetPath);

		var rq = Assertions.assertTimeoutPreemptively(timeout, () -> server.takeRequest());
		Assertions.assertEquals("MOVE", rq.getMethod());
		Assertions.assertEquals("infinity", rq.getHeader("Depth"));
		Assertions.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
		Assertions.assertNull(rq.getHeader("Overwrite"));
		Assertions.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
	}

	private MockResponse getInterceptedResponse(final String testResource) {
		return getInterceptedResponse(200, load(testResource));
	}

	private MockResponse getInterceptedResponse() {
		return getInterceptedResponse(201, "");
	}

	private MockResponse getInterceptedResponse(int httpCode, final String body) {
		return new MockResponse().setResponseCode(httpCode).setHeader("DAV", "1,2,3, hyperactive-access").setBody(body);
	}

	private String load(String resourceName) {
		final var in = getClass().getResourceAsStream("/webdav-test-responses/" + resourceName);
		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
	}

}