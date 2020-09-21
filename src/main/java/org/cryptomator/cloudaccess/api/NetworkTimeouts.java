package org.cryptomator.cloudaccess.api;

import java.util.concurrent.TimeUnit;

public class NetworkTimeouts {

	private final Timeout connection;
	private final Timeout read;
	private final Timeout write;

	NetworkTimeouts() {
		this.connection = new Timeout(Long.getLong("org.cryptomator.cloudaccess.timeout.connection", 30L), TimeUnit.SECONDS);
		this.read = new Timeout(Long.getLong("org.cryptomator.cloudaccess.timeout.read", 30L), TimeUnit.SECONDS);
		this.write = new Timeout(Long.getLong("org.cryptomator.cloudaccess.timeout.write", 30L), TimeUnit.SECONDS);
	}

	public static NetworkTimeouts createBySystemPropertiesOrDefaults() {
		return new NetworkTimeouts();
	}

	public Timeout connection() {
		return connection;
	}

	public Timeout read() {
		return read;
	}

	public Timeout write() {
		return write;
	}

	public static class Timeout {

		private final long timeout;
		private final TimeUnit unit;

		Timeout(final long timeout, final TimeUnit unit) {
			this.timeout = timeout;
			this.unit = unit;
		}

		public long getTimeout() {
			return timeout;
		}

		public TimeUnit getUnit() {
			return unit;
		}

		public long asMilliseconds() {
			return unit.toMillis(timeout);
		}

	}
}
