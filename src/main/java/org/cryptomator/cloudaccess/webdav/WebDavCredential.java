package org.cryptomator.cloudaccess.webdav;

import java.net.URL;

public class WebDavCredential {

	private final URL baseUrl;
	private final String username;
	private final String password;

	private WebDavCredential(final URL baseUrl, final String username, final String password) {
		this.baseUrl = baseUrl;
		this.username = username;
		this.password = password;
	}

	public static WebDavCredential from(final URL baseUrl, final String username, final String password) {
		return new WebDavCredential(baseUrl, username, password);
	}

	public URL getBaseUrl() {
		return baseUrl;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
}
