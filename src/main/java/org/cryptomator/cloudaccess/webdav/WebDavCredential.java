package org.cryptomator.cloudaccess.webdav;

import java.nio.file.Path;

public class WebDavCredential {

    private final Path baseUrl;
    private final String username;
    private final String password;

    public static WebDavCredential from(final Path baseUrl, final String username, final String password) {
        return new WebDavCredential(baseUrl, username, password);
    }

    private WebDavCredential(final Path baseUrl, final String username, final String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    public Path getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
