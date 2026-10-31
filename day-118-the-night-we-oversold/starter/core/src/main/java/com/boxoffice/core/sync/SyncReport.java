package com.boxoffice.core.sync;

/**
 * What one round of syncing did.
 *
 * @param problem why it stopped early, or "" when everything went
 */
public record SyncReport(int sent, int waiting, String problem) {

    public boolean allSent() {
        return waiting == 0;
    }

    /** "Sent 2 sales. Nothing waiting." or "Sent 1 sale. 2 still waiting: ..." */
    public String describe() {
        String start = "Sent " + sent + (sent == 1 ? " sale. " : " sales. ");
        if (waiting == 0) {
            return start + "Nothing waiting.";
        }
        return start + waiting + " still waiting" + (problem.isEmpty() ? "." : ": " + problem);
    }
}
