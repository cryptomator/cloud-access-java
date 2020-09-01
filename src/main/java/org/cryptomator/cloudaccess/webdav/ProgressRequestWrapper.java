package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;
import org.cryptomator.cloudaccess.api.ProgressListener;

import java.io.IOException;

public class ProgressRequestWrapper extends RequestBody {

	protected RequestBody delegate;
	protected ProgressListener listener;

	public ProgressRequestWrapper(final RequestBody delegate, final ProgressListener listener) {
		this.delegate = delegate;
		this.listener = listener;
	}

	@Override
	public MediaType contentType() {
		return delegate.contentType();
	}

	@Override
	public long contentLength() throws IOException {
		return delegate.contentLength();
	}

	@Override
	public void writeTo(final BufferedSink sink) throws IOException {
		final var countingSink = new CountingSink(sink);

		final var bufferedSink = Okio.buffer(countingSink);

		delegate.writeTo(bufferedSink);

		bufferedSink.flush();
	}

	protected final class CountingSink extends ForwardingSink {

		private long bytesWritten = 0;

		public CountingSink(Sink delegate) {
			super(delegate);
		}

		@Override
		public void write(final Buffer source, final long byteCount) throws IOException {
			super.write(source, byteCount);

			bytesWritten += byteCount;
			listener.onProgress(bytesWritten);
		}

	}
}
