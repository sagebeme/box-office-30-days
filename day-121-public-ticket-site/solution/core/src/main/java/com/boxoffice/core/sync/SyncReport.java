package com.boxoffice.core.sync;

/**
 * What one round of syncing did.
 *
 * @param sent      sales the server took
 * @param problem   why it stopped early, or "" when everything went
 * @param conflicts sales the server refused because the places had gone (Day 120)
 */
public record SyncReport(int sent, int waiting, String problem, int conflicts) {

    public SyncReport(int sent, int waiting, String problem) {
        this(sent, waiting, problem, 0);
    }

    public boolean allSent() {
        return waiting == 0;
    }

    /** "Sent 2 sales. Nothing waiting." or "Sent 1 sale. 2 still waiting: ..." */
    public String describe() {
        String start = "Sent " + sent + (sent == 1 ? " sale. " : " sales. ");
        String rest = waiting == 0
                ? "Nothing waiting."
                : waiting + " still waiting" + (problem.isEmpty() ? "." : ": " + problem);
        if (conflicts > 0) {
            rest += " " + conflicts + (conflicts == 1 ? " sale was" : " sales were")
                    + " oversold: run conflicts to see who.";
        }
        return start + rest;
    }
}
