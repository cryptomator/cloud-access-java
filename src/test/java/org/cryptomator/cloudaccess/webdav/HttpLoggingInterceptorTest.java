package org.cryptomator.cloudaccess.webdav;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;

public class HttpLoggingInterceptorTest {

    private final HttpLoggingInterceptor.Logger logger = Mockito.mock(HttpLoggingInterceptor.Logger.class);
    private final HttpLoggingInterceptor.Logger spyLogger = Mockito.spy(logger);

    private final Interceptor.Chain chain = Mockito.mock(Interceptor.Chain.class);

    private final Path baseUrl = Path.of("https://www.nextcloud.com/cloud/remote.php/webdav");

    @Test
    public void testLogging() throws IOException {

        final var httpLoggingInterceptor = new HttpLoggingInterceptor(spyLogger);

        final var request = new Request.Builder()
                .url(baseUrl.toString())
                .build();

        final var response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .addHeader("Authorization", "Basic Fooo")
                .addHeader("LoggedHeader", "Bar")
                .body(ResponseBody.create("Foo", MediaType.parse("application/json; charset=utf-8")))
                .message("")
                .build();

        Mockito.when(chain.request()).thenReturn(request);
        Mockito.when(chain.proceed(ArgumentMatchers.any())).thenReturn(response);

        httpLoggingInterceptor.intercept(chain);

        Mockito.verify(spyLogger).log("--> GET https://www.nextcloud.com/cloud/remote.php/webdav http/1.1 (unknown length)");
        Mockito.verify(spyLogger).log("--> END GET");
        Mockito.verify(spyLogger).log("<-- 200  https://www.nextcloud.com/cloud/remote.php/webdav (0ms)");
        Mockito.verify(spyLogger).log("LoggedHeader: Bar");
        Mockito.verify(spyLogger).log("<-- END HTTP");
    }
}