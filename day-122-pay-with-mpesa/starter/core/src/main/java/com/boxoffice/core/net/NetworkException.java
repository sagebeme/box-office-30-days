package com.boxoffice.core.net;

/** No useful answer from the server, even after every attempt we were allowed. */
public final class NetworkException extends RuntimeException {

    private final int attempts;

    public NetworkException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}
