package com.boxoffice.core.sync;

/**
 * The server has the sale, and said no to it: another gate sold the last
 * places first. Not a network problem, and sending it again won't change the
 * answer, so the outbox moves on to the next sale and staff are told.
 */
public final class SaleConflict extends RuntimeException {

    public SaleConflict(String detail) {
        super(detail);
    }
}
