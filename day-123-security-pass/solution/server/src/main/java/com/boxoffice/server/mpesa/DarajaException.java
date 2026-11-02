package com.boxoffice.server.mpesa;

/** Daraja said no, or couldn't be reached. The message is fit to show a fan. */
public class DarajaException extends RuntimeException {

    public DarajaException(String message) {
        super(message);
    }

    public DarajaException(String message, Throwable cause) {
        super(message, cause);
    }
}
