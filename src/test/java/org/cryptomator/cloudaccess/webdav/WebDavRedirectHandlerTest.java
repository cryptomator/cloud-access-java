package org.cryptomator.cloudaccess.webdav;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Path;

public class WebDavRedirectHandlerTest {

	private final OkHttpClient mockedOkHttpClient = Mockito.mock(OkHttpClient.class);
	private final Call remoteCall = Mockito.mock(Call.class);
	private WebDavRedirectHandler webDavRedirectHandler;

	private URL baseUrl;
	private URL redirectUrl;
	private URL targetUrl;

	@BeforeEach
	public void setUp() throws MalformedURLException {
		webDavRedirectHandler = new WebDavRedirectHandler(mockedOkHttpClient);
		baseUrl = new URL("https://www.nextcloud.com/cloud/remote.php/webdav");
		redirectUrl = new URL("https://www.nextcloud.com/cloud/remote.php/webdav/redirected");
		targetUrl = new URL("https://www.nextcloud.com/cloud/remote.php/webdav/target");
	}

	@Test
	public void testRedirect() throws IOException {
		final var request = new Request.Builder()
				.url(baseUrl)
				.build();

		Mockito.when(remoteCall.execute())
				.thenReturn(mockedRedirectResponse(baseUrl, 302))
				.thenReturn(mockedRedirectResponse(redirectUrl, 302))
				.thenReturn(mockedRedirectResponse(targetUrl, 200));

		Mockito.when(mockedOkHttpClient.newCall(ArgumentMatchers.any())).thenReturn(remoteCall);

		final var response = webDavRedirectHandler.executeFollowingRedirects(request);

		Assertions.assertEquals(mockedRedirectResponse(targetUrl, 200).toString(), response.toString());
	}

	@Test
	public void testRedirectWithoutLocationInHeaderLeadsToNoRedirect() throws IOException {
		final var request = new Request.Builder()
				.url(baseUrl)
				.build();

		Mockito.when(remoteCall.execute())
				.thenReturn(mockedRedirectResponse(baseUrl, 302, false))
				.thenReturn(mockedRedirectResponse(targetUrl, 200));

		Mockito.when(mockedOkHttpClient.newCall(ArgumentMatchers.any())).thenReturn(remoteCall);

		final var response = webDavRedirectHandler.executeFollowingRedirects(request);

		Assertions.assertEquals(mockedRedirectResponse(baseUrl, 302).toString(), response.toString());
	}

	@Test
	public void testTooManyRedirectsThrowsProtocolException() throws IOException {
		final var request = new Request.Builder()
				.url(baseUrl)
				.build();

		Mockito.when(remoteCall.execute())
				.thenReturn(mockedRedirectResponse(baseUrl, 300))
				.thenReturn(mockedRedirectResponse(redirectUrl, 301))
				.thenReturn(mockedRedirectResponse(redirectUrl, 302))
				.thenReturn(mockedRedirectResponse(redirectUrl, 307))
				.thenReturn(mockedRedirectResponse(redirectUrl, 308))
				.thenReturn(mockedRedirectResponse(redirectUrl, 300))
				.thenReturn(mockedRedirectResponse(redirectUrl, 301))
				.thenReturn(mockedRedirectResponse(redirectUrl, 302))
				.thenReturn(mockedRedirectResponse(redirectUrl, 307))
				.thenReturn(mockedRedirectResponse(redirectUrl, 308))
				.thenReturn(mockedRedirectResponse(redirectUrl, 300))
				.thenReturn(mockedRedirectResponse(redirectUrl, 301))
				.thenReturn(mockedRedirectResponse(redirectUrl, 302))
				.thenReturn(mockedRedirectResponse(redirectUrl, 307))
				.thenReturn(mockedRedirectResponse(redirectUrl, 308))
				.thenReturn(mockedRedirectResponse(redirectUrl, 300))
				.thenReturn(mockedRedirectResponse(redirectUrl, 301))
				.thenReturn(mockedRedirectResponse(redirectUrl, 302))
				.thenReturn(mockedRedirectResponse(redirectUrl, 307))
				.thenReturn(mockedRedirectResponse(redirectUrl, 308))
				.thenReturn(mockedRedirectResponse(redirectUrl, 300))
				.thenReturn(mockedRedirectResponse(targetUrl, 200));

		Mockito.when(mockedOkHttpClient.newCall(ArgumentMatchers.any())).thenReturn(remoteCall);

		final var exception = Assertions.assertThrows(ProtocolException.class, () -> webDavRedirectHandler.executeFollowingRedirects(request));
		Assertions.assertTrue(exception.getMessage().contains("Too many redirects: 21"));
	}

	private Response mockedRedirectResponse(final URL url, int httpStatusCode) {
		return mockedRedirectResponse(url, httpStatusCode, true);
	}

	private Response mockedRedirectResponse(final URL url, int httpStatusCode, boolean locationHeader) {
		var responseBuilder = new Response.Builder()
				.request(new Request.Builder()
						.url(url)
						.build())
				.protocol(Protocol.HTTP_1_1)
				.code(httpStatusCode)
				.body(ResponseBody.create("", MediaType.parse("application/json; charset=utf-8")))
				.message("");

		if (locationHeader) {
			responseBuilder = responseBuilder.addHeader("Location", redirectUrl.toString());
		}

		return responseBuilder.build();
	}
}