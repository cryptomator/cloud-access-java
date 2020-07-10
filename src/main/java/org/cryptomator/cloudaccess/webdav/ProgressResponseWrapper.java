package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import org.cryptomator.cloudaccess.api.ProgressListener;

import java.io.IOException;

class ProgressResponseWrapper extends ResponseBody {

    private final ResponseBody delegate;
    private final ProgressListener progressListener;
    private final int EOF = -1;
    private BufferedSource bufferedSource;

    ProgressResponseWrapper(final ResponseBody delegate, final ProgressListener progressListener) {
        this.delegate = delegate;
        this.progressListener = progressListener;
    }

    @Override public MediaType contentType() {
        return delegate.contentType();
    }

    @Override public long contentLength() {
        return delegate.contentLength();
    }

    @Override public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(delegate.source()));
        }
        return bufferedSource;
    }

    private Source source(final Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;

            @Override public long read(final Buffer sink, final long byteCount) throws IOException {
                final var bytesRead = super.read(sink, byteCount);
                totalBytesRead += bytesRead != EOF ? bytesRead : 0;
                if(bytesRead != EOF) {
                    progressListener.onProgress(totalBytesRead);
                }
                return bytesRead;
            }
        };
    }
}