package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Preconditions;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamRequestBody extends RequestBody {
	private final InputStream inputStream;
	private final long size;

	private InputStreamRequestBody(final InputStream inputStream, long size) {
		Preconditions.checkNotNull(inputStream, "Inputstream must be provided");
		Preconditions.checkArgument(size >= 0, "Size must be positive");
		this.inputStream = inputStream;
		this.size = size;
	}

	public static RequestBody from(final InputStream inputStream, long size) {
		return new InputStreamRequestBody(inputStream, size);
	}

	@Override
	public MediaType contentType() {
		return MediaType.parse("application/octet-stream");
	}

	@Override
	public long contentLength() {
		return size;
	}

	@Override
	public void writeTo(final BufferedSink sink) throws IOException {
		try (final var source = Okio.source(inputStream)) {
			sink.writeAll(source);
		}
	}
}