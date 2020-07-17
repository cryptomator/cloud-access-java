package org.cryptomator.cloudaccess.vaultformat8;

import com.google.common.base.Preconditions;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class OffsetInputStream extends FilterInputStream {

	private long toBeSkipped;

	/**
	 * Creates an OffsetInputStream which skips a given number of bytes before {@link #read()} begins to return content.
	 *
	 * @param in     the underlying input stream
	 * @param offset The number of bytes to skip on <code>in</code>
	 */
	protected OffsetInputStream(InputStream in, long offset) {
		super(in);
		Preconditions.checkArgument(offset >= 0, "offset must be non-negative");
		this.toBeSkipped = offset;
	}

	// discards bytes until toBeSkipped bytes have been skipped or EOF has been reached
	private void skipToOffset() throws IOException {
		byte[] buf = new byte[1024];
		while (toBeSkipped > 0) {
			int read = in.read(buf, 0, (int) Math.min(buf.length, toBeSkipped));
			if (read == -1) {
				toBeSkipped = 0;
			} else {
				toBeSkipped -= read;
			}
		}
	}

	@Override
	public int available() throws IOException {
		return (int) Math.min(0, in.available() - toBeSkipped);
	}

	@Override
	public long skip(long n) throws IOException {
		skipToOffset();
		return super.skip(n);
	}

	@Override
	public int read() throws IOException {
		skipToOffset();
		return super.read();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		skipToOffset();
		return super.read(b, off, len);
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public synchronized void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}
}
