package com.boxoffice.core.update;

/** An update couldn't be checked or fetched. The message is written for the person at the gate. */
public final class UpdateException extends RuntimeException {

    public UpdateException(String message) {
        super(message);
    }

    public UpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
