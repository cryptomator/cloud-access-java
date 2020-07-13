package org.cryptomator.cloudaccess.webdav;

import java.util.HashSet;
import java.util.Set;

class HeaderNames {

    private final Set<String> lowercaseNames = new HashSet<>();

    public HeaderNames(final String... headerNames) {
        for (final var headerName : headerNames) {
            lowercaseNames.add(headerName.toLowerCase());
        }
    }

    public boolean contains(final String headerName) {
        return lowercaseNames.contains(headerName.toLowerCase());
    }

}
