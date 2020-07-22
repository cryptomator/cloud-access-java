package org.cryptomator.cloudaccess.webdav;

import com.google.common.base.Strings;
import okhttp3.MediaType;
import okio.Buffer;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ProgressRequestWrapperTest {

	private static final String CONTENT = Strings.repeat("Lorem ipsum\n", 1000);

	@DisplayName("test ProgressRequestWrapper.writeTo() updates ProgressListener at least once")
	@Test
	public void updateProgressWhenWriteToProgressRequestWrapper() throws IOException {
		//Order of execution is important here
		final var buffer = new Buffer();
		final var progressListener = Mockito.mock(ProgressListener.class);
		final var progressRequestWrapper = new ProgressRequestWrapper(InputStreamRequestBody.from(load()), progressListener);

		Assertions.assertEquals(CONTENT.getBytes().length, progressRequestWrapper.contentLength());
		Assertions.assertEquals(MediaType.parse("application/octet-stream"), progressRequestWrapper.contentType());

		progressRequestWrapper.writeTo(buffer);
		buffer.flush();

		Assertions.assertEquals(CONTENT, buffer.readString(StandardCharsets.UTF_8));
		Mockito.verify(progressListener).onProgress(CONTENT.getBytes().length);
	}

	private InputStream load() {
		return new ByteArrayInputStream(CONTENT.getBytes());
	}

}