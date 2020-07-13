package org.cryptomator.cloudaccess.webdav;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.file.Path;

class WebDavRedirectHandlerTest {

    private final OkHttpClient mockedOkHttpClient = Mockito.mock(OkHttpClient.class);
    private final Call remoteCall = Mockito.mock(Call.class);


    private WebDavRedirectHandler webDavRedirectHandler;

    private final Path baseUrl = Path.of("https://www.nextcloud.com/cloud/remote.php/webdav");
    private final Path redirectUrl = Path.of("https://www.nextcloud.com/cloud/remote.php/webdav/redirected");
    private final Path targetUrl = Path.of("https://www.nextcloud.com/cloud/remote.php/webdav/target");

    @BeforeEach
    public void setUp() {
        webDavRedirectHandler = new WebDavRedirectHandler(mockedOkHttpClient);
    }

    @Test
    public void testRedirect() throws IOException {
        final var request = new Request.Builder()
                .url(baseUrl.toString())
                .build();

        Mockito.when(remoteCall.execute())
                .thenReturn(mockedRedirectResponse(baseUrl, 302))
                .thenReturn(mockedRedirectResponse(redirectUrl, 302))
                .thenReturn(mockedRedirectResponse(targetUrl, 200));

        Mockito.when(mockedOkHttpClient.newCall(ArgumentMatchers.any())).thenReturn(remoteCall);

        final var response = webDavRedirectHandler.executeFollowingRedirects(request);

        Assert.assertEquals(mockedRedirectResponse(targetUrl, 200).toString(), response.toString());
    }

    @Test
    public void testRedirectWithoutLocationInHeaderLeadsToNoRedirect() throws IOException {
        final var request = new Request.Builder()
                .url(baseUrl.toString())
                .build();

        Mockito.when(remoteCall.execute())
                .thenReturn(mockedRedirectResponse(baseUrl, 302, false))
                .thenReturn(mockedRedirectResponse(targetUrl, 200));

        Mockito.when(mockedOkHttpClient.newCall(ArgumentMatchers.any())).thenReturn(remoteCall);

        final var response = webDavRedirectHandler.executeFollowingRedirects(request);

        Assert.assertEquals(mockedRedirectResponse(baseUrl, 302).toString(), response.toString());
    }

    @Test
    public void testTooManyRedirectsThrowsProtocolException() throws IOException {
        final var request = new Request.Builder()
                .url(baseUrl.toString())
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
        Assert.assertTrue(exception.getMessage().contains("Too many redirects: 21"));
    }

    private Response mockedRedirectResponse(final Path url, int httpStatusCode) {
        return mockedRedirectResponse(url, httpStatusCode, true);
    }

    private Response mockedRedirectResponse(final Path url, int httpStatusCode, boolean locationHeader) {
        var responseBuilder = new Response.Builder()
                .request(new Request.Builder()
                        .url(url.toString())
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(httpStatusCode)
                .body(ResponseBody.create("", MediaType.parse("application/json; charset=utf-8")))
                .message("");

        if(locationHeader) {
            responseBuilder = responseBuilder.addHeader("Location", redirectUrl.toString());
        }

        return responseBuilder.build();
    }
}