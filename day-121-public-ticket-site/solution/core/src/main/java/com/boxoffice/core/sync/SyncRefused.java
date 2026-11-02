package com.boxoffice.core.sync;

/** The server got the sale and said no. Sending it again as it is won't change that. */
public final class SyncRefused extends RuntimeException {

    public SyncRefused(String message) {
        super(message);
    }
}
