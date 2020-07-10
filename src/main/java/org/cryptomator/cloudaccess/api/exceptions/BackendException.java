package org.cryptomator.cloudaccess.api.exceptions;

import java.util.concurrent.CompletionException;

public class BackendException extends CompletionException {

    public BackendException() {
        super();
    }

    public BackendException(Throwable e) {
        super(e);
    }

    public BackendException(String message) {
        super(message);
    }

    public BackendException(String message, Throwable e) {
        super(message, e);
    }

}
