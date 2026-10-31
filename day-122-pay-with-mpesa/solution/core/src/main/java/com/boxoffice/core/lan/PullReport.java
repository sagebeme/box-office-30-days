package com.boxoffice.core.lan;

/** What one pull from another gate brought in. */
public record PullReport(String peer, int sales, int tickets) {

    /** "Got 2 sales (5 tickets) from gate B" */
    public String describe() {
        return "Got " + sales + (sales == 1 ? " sale" : " sales")
                + " (" + tickets + (tickets == 1 ? " ticket" : " tickets") + ") from gate " + peer;
    }
}
