package org.cryptomator.cloudaccess.webdav;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

class WebDavCompatibleHttpClient {

	private static final Logger LOG = LoggerFactory.getLogger(WebDavCompatibleHttpClient.class);

	private final WebDavRedirectHandler webDavRedirectHandler;

	WebDavCompatibleHttpClient(final WebDavCredential webDavCredential, WebDavProviderConfig config) {
		this.webDavRedirectHandler = new WebDavRedirectHandler(httpClientFor(webDavCredential, config));
	}

	private static OkHttpClient httpClientFor(final WebDavCredential webDavCredential, WebDavProviderConfig config) {
		final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();
		final var builder = new OkHttpClient()
				.newBuilder()
				.connectTimeout(config.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
				.readTimeout(config.getReadTimeoutSeconds(), TimeUnit.SECONDS)
				.writeTimeout(config.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
				.followRedirects(false)
				.addInterceptor(new HttpLoggingInterceptor(LOG::trace))
				.authenticator(httpAuthenticator(webDavCredential.getUsername(), webDavCredential.getPassword(), authCache))
				.addInterceptor(new AuthenticationCacheInterceptor(authCache));
		return builder.build();
	}

	private static Authenticator httpAuthenticator(final String username, final String password, final Map<String, CachingAuthenticator> authCache) {
		final var credentials = new Credentials(username, password);
		final var digestAuthenticator = new DigestAuthenticator(credentials);
		final var basicAuthenticator = new BasicAuthenticator(credentials, StandardCharsets.UTF_8);

		final var dispatchingAuthenticator = new DispatchingAuthenticator
				.Builder()
				.with("digest", digestAuthenticator)
				.with("basic", basicAuthenticator)
				.build();

		return new CachingAuthenticatorDecorator(dispatchingAuthenticator, authCache);
	}

	Response execute(final Request.Builder requestBuilder) throws IOException {
		return execute(requestBuilder.build());
	}

	private Response execute(final Request request) throws IOException {
		return webDavRedirectHandler.executeFollowingRedirects(request);
	}

}
