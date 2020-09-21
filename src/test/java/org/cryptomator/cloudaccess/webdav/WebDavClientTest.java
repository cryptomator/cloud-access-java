package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.QuotaNotAvailableException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WebDavClientTest {

	private final WebDavCompatibleHttpClient webDavCompatibleHttpClient = Mockito.mock(WebDavCompatibleHttpClient.class);
	private final CloudItemMetadata testFolderDocuments = new CloudItemMetadata("Documents", CloudPath.of("/Documents"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private final CloudItemMetadata testFileManual = new CloudItemMetadata("Nextcloud Manual.pdf", CloudPath.of("/Nextcloud Manual.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(6837751L));
	private final CloudItemMetadata testFileIntro = new CloudItemMetadata("Nextcloud intro.mp4", CloudPath.of("/Nextcloud intro.mp4"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(462413L));
	private final CloudItemMetadata testFilePng = new CloudItemMetadata("Nextcloud.png", CloudPath.of("/Nextcloud.png"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(37042L));
	private final CloudItemMetadata testFolderPhotos = new CloudItemMetadata("Photos", CloudPath.of("/Photos"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private WebDavClient webDavClient;
	private URL baseUrl;

	@BeforeEach
	public void setup() throws MalformedURLException {
		baseUrl = new URL("https://www.nextcloud.com/cloud/remote.php/webdav");
		final var webDavCredential = WebDavCredential.from(baseUrl, "foo", "bar");
		webDavClient = new WebDavClient(webDavCompatibleHttpClient, webDavCredential);
	}

	@ParameterizedTest(name = "absoluteURLFrom(\"{0}\") == {1}")
	@DisplayName("absoluteURLFrom(...)")
	@CsvSource(value = {
			"'',/cloud/remote.php/webdav",
			"/,/cloud/remote.php/webdav",
			"/foo,/cloud/remote.php/webdav/foo",
			"foo/bar,/cloud/remote.php/webdav/foo/bar",
			"/foo///bar/baz,/cloud/remote.php/webdav/foo/bar/baz",
	})
	public void testAbsoluteURLFrom(String absPath, String expectedResult) {
		var result = webDavClient.absoluteURLFrom(CloudPath.of(absPath));

		Assertions.assertEquals(expectedResult, result.getPath());
	}

	@Test
	@DisplayName("get metadata of /Nextcloud Manual.pdf")
	public void testItemMetadata() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-meta-data-response.xml"));

		final var itemMetadata = webDavClient.itemMetadata(CloudPath.of("/Nextcloud Manual.pdf"));

		Assertions.assertEquals(testFileManual, itemMetadata);
	}

	@Test
	@DisplayName("get quota of /")
	public void testQuota() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "quota.xml"));

		final var quota = webDavClient.quota(CloudPath.of("/"));

		Assertions.assertEquals(10699503366L, quota.getAvailableBytes());
		Assertions.assertEquals(37914874L, quota.getUsedBytes().get());
		Assertions.assertEquals(Optional.empty(), quota.getTotalBytes());
	}

	@Test
	@DisplayName("get quota of / with negative available")
	public void testQuotaWithNegativeAvailable() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "quota-negative-available.xml"));

		Assertions.assertThrows(QuotaNotAvailableException.class, () -> webDavClient.quota(CloudPath.of("/")));
	}

	@Test
	@DisplayName("list /")
	public void testList() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "directory-list-response.xml"));

		final var nodeList = webDavClient.list(CloudPath.of("/"));

		final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos);

		Assertions.assertEquals(expectedList, nodeList.getItems());

		Assertions.assertTrue(nodeList.getNextPageToken().isEmpty());
	}

	@Test
	@DisplayName("read /Documents/About.txt (Error 404)")
	public void testReadNotFound() throws IOException {
		var response = Mockito.mock(Response.class);
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(response);
		Mockito.when(response.code()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

		Assertions.assertThrows(NotFoundException.class, () -> {
			webDavClient.read(CloudPath.of("/Documents/About.txt"), ProgressListener.NO_PROGRESS_AWARE);
		});
		Mockito.verify(response).close();
	}

	@Test
	@DisplayName("read /Documents/About.txt (complete)")
	public void testRead() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-read-response.txt"));

		final var inputStream = webDavClient.read(CloudPath.of("/Documents/About.txt"), ProgressListener.NO_PROGRESS_AWARE);
		final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

		Assertions.assertEquals(load("item-read-response.txt"), content);
	}

	@Test
	@DisplayName("read /Documents/About.txt (bytes 4-6)")
	public void testRandomAccessRead() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-partial-read-response.txt"));

		final var inputStream = webDavClient.read(CloudPath.of("/Documents/About.txt"), 4, 2, ProgressListener.NO_PROGRESS_AWARE);
		final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

		Assertions.assertEquals(load("item-partial-read-response.txt"), content);
	}

	@Test
	@DisplayName("write to /foo.txt (non-existing, replace)")
	public void testWriteToAndReplaceNewFile() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl))
				.thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

		final var writtenItemMetadata = new CloudItemMetadata("foo.txt", CloudPath.of("/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

		InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
		webDavClient.write(CloudPath.of("/foo.txt"), true, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
	}

	@Test
	@DisplayName("write to /foo.txt (non-existing)")
	public void testWriteToNewFile() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, 404, ""))
				.thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

		InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
		webDavClient.write(CloudPath.of("/foo.txt"), false, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
	}

	@Test
	@DisplayName("write to /file (already existing)")
	public void testWriteToExistingFile() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

		InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

		Assertions.assertThrows(AlreadyExistsException.class, () -> {
			webDavClient.write(CloudPath.of("/foo.txt"), false, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
		});
	}

	@Test
	@DisplayName("write to /foo.txt (replace existing)")
	public void testWriteToAndReplaceExistingFile() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"))
				.thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

		final var writtenItemMetadata = new CloudItemMetadata("foo.txt", CloudPath.of("/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

		InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
		webDavClient.write(CloudPath.of("/foo.txt"), true, inputStream, inputStream.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
	}

	@Test
	@DisplayName("create /foo")
	public void testCreateFolder() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, 404, ""))
				.thenReturn(getInterceptedResponse(baseUrl));

		final var path = webDavClient.createFolder(CloudPath.of("/foo"));

		Assertions.assertEquals(CloudPath.of("/foo"), path);
	}

	@Test
	@DisplayName("delete /foo.txt")
	public void testDeleteFile() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl));

		webDavClient.delete(CloudPath.of("/foo.txt"));
	}

	@Test
	@DisplayName("delete /foo (recursively)")
	public void testDeleteFolder() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl));

		webDavClient.delete(CloudPath.of("/foo"));
	}

	@Test
	@DisplayName("move /foo -> /bar (non-existing)")
	public void testMoveToNonExisting() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl));

		final var targetPath = webDavClient.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false);

		Assertions.assertEquals(CloudPath.of("/bar"), targetPath);
	}

	@Test
	@DisplayName("move /foo -> /bar (already exists)")
	public void testMoveToExisting() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, 412, "item-move-exists-no-replace.xml"));

		Assertions.assertThrows(AlreadyExistsException.class, () -> {
			final var targetPath = webDavClient.move(CloudPath.of("/foo"), CloudPath.of("/bar"), false);
			Assertions.assertNull(targetPath);
		});
	}

	@Test
	@DisplayName("move /foo -> /bar (replace existing)")
	public void testMoveToAndReplaceExisting() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, 204, ""));

		final var targetPath = webDavClient.move(CloudPath.of("/foo"), CloudPath.of("/bar"), true);

		Assertions.assertEquals(CloudPath.of("/bar"), targetPath);
	}

	@Test
	@DisplayName("check if server recognizes WebDAV servers")
	public void testCheckServerCompatibility() throws IOException {
		final var davResponse = new Response.Builder()
				.request(new Request.Builder()
						.url(baseUrl.toString())
						.build())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.addHeader("DAV", "1, 3, extended-mkcol")
				.body(ResponseBody.create("", MediaType.parse("application/json; charset=utf-8")))
				.message("")
				.build();

		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(davResponse)
				.thenReturn(getInterceptedResponse(baseUrl));

		webDavClient.checkServerCompatibility();

		Assertions.assertThrows(ServerNotWebdavCompatibleException.class, () -> webDavClient.checkServerCompatibility());
	}

	@Test
	@DisplayName("check if client can authenticate against server (auth succeeded)")
	public void testTryAuthenticatedRequestSuccess() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, "authentication-response.xml"));

		webDavClient.tryAuthenticatedRequest();
	}

	@Test
	@DisplayName("check if client can authenticate against server (auth failed)")
	public void testTryAuthenticatedRequestUnauthorized() throws IOException {
		Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
				.thenReturn(getInterceptedResponse(baseUrl, 401, ""));

		Assertions.assertThrows(UnauthorizedException.class, () -> webDavClient.tryAuthenticatedRequest());
	}

	private Response getInterceptedResponse(final URL url, final String testResource) {
		return getInterceptedResponse(url, 200, load(testResource));
	}

	private Response getInterceptedResponse(final URL url) {
		return getInterceptedResponse(url, 201, "");
	}

	private Response getInterceptedResponse(final URL url, int httpCode, final String body) {
		return new Response.Builder()
				.request(new Request.Builder()
						.url(url)
						.build())
				.protocol(Protocol.HTTP_1_1)
				.code(httpCode)
				.body(ResponseBody.create(body, MediaType.parse("application/json; charset=utf-8")))
				.message("")
				.build();
	}

	private String load(String resourceName) {
		final var in = getClass().getResourceAsStream("/webdav-test-responses/" + resourceName);
		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
	}
}