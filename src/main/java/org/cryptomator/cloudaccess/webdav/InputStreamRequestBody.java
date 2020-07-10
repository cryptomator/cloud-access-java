package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamRequestBody extends RequestBody {
    private final InputStream inputStream;

    public static RequestBody from(final InputStream inputStream) {
        return new InputStreamRequestBody(inputStream);
    }

    private InputStreamRequestBody(final InputStream inputStream) {
        if (inputStream == null) throw new NullPointerException("inputStream == null");
        this.inputStream = inputStream;
    }

    @Override
    public MediaType contentType() {
        return MediaType.parse("application/octet-stream");
    }

    @Override
    public long contentLength() throws IOException {
        return inputStream.available() == 0 ? -1 : inputStream.available();
    }

    @Override
    public void writeTo(final BufferedSink sink) throws IOException {
        try(final var source = Okio.source(inputStream)) {
            sink.writeAll(source);
        }
    }
}