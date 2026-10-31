package com.boxoffice.core;

/** The database let the gate down. Wraps JDBC's checked SQLException. */
public class StoreException extends RuntimeException {

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
