package org.cryptomator.cloudaccess.webdav;

public class WebDavProviderConfig {

	private static final int DEFAULT_CONNECTION_TIMEOUT = 30;
	private static final int DEFAULT_READ_TIMEOUT = 30;
	private static final int DEFAULT_WRITE_TIMEOUT = 30;

	private final int connectionTimeoutSeconds;
	private final int readTimeoutSeconds;
	private final int writeTimeoutSeconds;

	WebDavProviderConfig() {
		this.connectionTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.connectionTimeoutSeconds", DEFAULT_CONNECTION_TIMEOUT);
		this.readTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.readTimeoutSeconds", DEFAULT_READ_TIMEOUT);
		this.writeTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.writeTimeoutSeconds", DEFAULT_WRITE_TIMEOUT);
	}

	public static WebDavProviderConfig createFromSystemPropertiesOrDefaults() {
		return new WebDavProviderConfig();
	}

	int getConnectionTimeoutSeconds() {
		return connectionTimeoutSeconds;
	}

	int getReadTimeoutSeconds() {
		return readTimeoutSeconds;
	}

	int getWriteTimeoutSeconds() {
		return writeTimeoutSeconds;
	}
}
