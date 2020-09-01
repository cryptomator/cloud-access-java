package org.cryptomator.cloudaccess.webdav;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class HttpLoggingInterceptor implements Interceptor {

	private static final HeaderNames EXCLUDED_HEADERS = new HeaderNames(//
			// headers excluded because they are logged separately:
			"Content-Type", "Content-Length",
			// headers excluded because they contain sensitive information:
			"Authorization", //
			"WWW-Authenticate", //
			"Cookie", //
			"Set-Cookie" //
	);
	private final Logger logger;

	public HttpLoggingInterceptor(final Logger logger) {
		this.logger = logger;
	}

	@Override
	public Response intercept(final Chain chain) throws IOException {
		return proceedWithLogging(chain);
	}

	private Response proceedWithLogging(final Chain chain) throws IOException {
		final var request = chain.request();
		logRequest(request, chain);
		return getAndLogResponse(request, chain);
	}

	private void logRequest(final Request request, final Chain chain) throws IOException {
		logRequestStart(request, chain);
		logContentTypeAndLength(request);
		logHeaders(request.headers());
		logRequestEnd(request);
	}

	private Response getAndLogResponse(final Request request, final Chain chain) throws IOException {
		final var startOfRequestMs = System.nanoTime();
		final var response = getResponseLoggingExceptions(request, chain);
		final var requestDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startOfRequestMs);
		logResponse(response, requestDurationMs);
		return response;
	}

	private Response getResponseLoggingExceptions(final Request request, final Chain chain) throws IOException {
		try {
			return chain.proceed(request);
		} catch (Exception e) {
			logger.log("<-- HTTP FAILED: " + e);
			throw e;
		}
	}

	private void logResponse(final Response response, final long requestDurationMs) {
		logResponseStart(response, requestDurationMs);
		logHeaders(response.headers());
		logger.log("<-- END HTTP");
	}

	private void logRequestStart(final Request request, final Chain chain) throws IOException {
		final var connection = chain.connection();
		final var protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
		final var bodyLength = hasBody(request) ? request.body().contentLength() + "-byte body" : "unknown length";

		logger.log(format("--> %s %s %s (%s)", //
				request.method(), //
				request.url(), //
				protocol, //
				bodyLength //
		));
	}

	private void logContentTypeAndLength(final Request request) throws IOException {
		// Request body headers are only present when installed as a network interceptor. Force
		// them to be included (when available) so there values are known.
		if (hasBody(request)) {
			final var body = request.body();
			if (body.contentType() != null) {
				logger.log("Content-Type: " + body.contentType());
			}
			if (body.contentLength() != -1) {
				logger.log("Content-Length: " + body.contentLength());
			}
		}
	}

	private void logRequestEnd(final Request request) throws IOException {
		logger.log("--> END " + request.method());
	}

	private void logResponseStart(final Response response, final long requestDurationMs) {
		logger.log("<-- " + response.code() + ' ' + response.message() + ' ' + response.request().url() + " (" + requestDurationMs + "ms" + ')');
	}

	private boolean hasBody(final Request request) {
		return request.body() != null;
	}

	private void logHeaders(final Headers headers) {
		for (int i = 0, count = headers.size(); i < count; i++) {
			final var name = headers.name(i);
			if (isExcludedHeader(name)) {
				continue;
			}
			logger.log(name + ": " + headers.value(i));
		}
	}

	private boolean isExcludedHeader(final String name) {
		return EXCLUDED_HEADERS.contains(name);
	}

	public interface Logger {
		void log(String message);
	}
}
