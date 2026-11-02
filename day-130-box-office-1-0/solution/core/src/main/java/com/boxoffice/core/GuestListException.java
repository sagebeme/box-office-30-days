package com.boxoffice.core;

/** The guest-list file has a line the gate can't read. The message names the line. */
public class GuestListException extends RuntimeException {

    public GuestListException(int lineNumber, String problem) {
        super("Line " + lineNumber + ": " + problem);
    }
}
