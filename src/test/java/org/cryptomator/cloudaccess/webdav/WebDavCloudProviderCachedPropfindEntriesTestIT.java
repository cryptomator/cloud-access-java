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

public class WebDavCloudProviderCachedPropfindEntriesTestIT {

	private final MockWebServer server;
	private final CloudProvider provider;
	private final URL baseUrl;
	private final Duration timeout = Duration.ofMillis(10000);

	private final CloudItemMetadata testFolderDocuments = new CloudItemMetadata("Documents", CloudPath.of("/Documents"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private final CloudItemMetadata testFileManual = new CloudItemMetadata("Nextcloud Manual.pdf", CloudPath.of("/Nextcloud Manual.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(6837751L));
	private final CloudItemMetadata testFileIntro = new CloudItemMetadata("Nextcloud intro.mp4", CloudPath.of("/Nextcloud intro.mp4"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(462413L));
	private final CloudItemMetadata testFilePng = new CloudItemMetadata("Nextcloud.png", CloudPath.of("/Nextcloud.png"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(37042L));
	private final CloudItemMetadata testFolderPhotos = new CloudItemMetadata("Photos", CloudPath.of("/Photos"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());

	private final String webDavRequestBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<d:propfind xmlns:d=\"DAV:\">\n<d:prop>\n<d:resourcetype />\n<d:getcontentlength />\n<d:getlastmodified />\n<d:getetag />\n</d:prop>\n</d:propfind>";

	public WebDavCloudProviderCachedPropfindEntriesTestIT() throws IOException, InterruptedException {
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

	private MockResponse getInterceptedResponse(final String testResource) {
		return getInterceptedResponse(200, load(testResource));
	}

	private MockResponse getInterceptedResponse(int httpCode, final String body) {
		return new MockResponse().setResponseCode(httpCode).setHeader("DAV", "1,2,3, hyperactive-access").setBody(body);
	}

	private String load(String resourceName) {
		final var in = getClass().getResourceAsStream("/webdav-test-responses/" + resourceName);
		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
	}

}