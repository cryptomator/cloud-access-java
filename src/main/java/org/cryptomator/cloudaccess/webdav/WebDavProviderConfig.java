package org.cryptomator.cloudaccess.webdav;

public class WebDavProviderConfig {

	private static final int DEFAULT_CONNECTION_TIMEOUT = 30;
	private static final int DEFAULT_READ_TIMEOUT = 30;
	private static final int DEFAULT_WRITE_TIMEOUT = 30;
	private static final int DEFAULT_REMOTE_CHANGE_POLLER_PERIOD = 60;
	private static final int DEFAULT_REMOTE_CHANGE_POLLER_INITIAL_DELAY = 90;

	private final int connectionTimeoutSeconds;
	private final int readTimeoutSeconds;
	private final int writeTimeoutSeconds;

	private final int remoteChangePollerPeriodSeconds;
	private final int remoteChangePollerInitialDelaySeconds;

	WebDavProviderConfig() {
		this.connectionTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.connectionTimeoutSeconds", DEFAULT_CONNECTION_TIMEOUT);
		this.readTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.readTimeoutSeconds", DEFAULT_READ_TIMEOUT);
		this.writeTimeoutSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.writeTimeoutSeconds", DEFAULT_WRITE_TIMEOUT);
		this.remoteChangePollerPeriodSeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.remoteChangePollerPeriodSeconds", DEFAULT_REMOTE_CHANGE_POLLER_PERIOD);
		this.remoteChangePollerInitialDelaySeconds = Integer.getInteger("org.cryptomator.cloudaccess.webdav.remoteChangePollerInitialDelaySeconds", DEFAULT_REMOTE_CHANGE_POLLER_INITIAL_DELAY);
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

	int getRemoteChangePollerPeriodSeconds() {
		return remoteChangePollerPeriodSeconds;
	}

	int getRemoteChangePollerInitialDelaySeconds() {
		return remoteChangePollerInitialDelaySeconds;
	}
}
