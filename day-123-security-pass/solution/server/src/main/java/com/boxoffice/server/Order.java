package com.boxoffice.server;

import com.boxoffice.core.Money;

/**
 * An order placed on the site. Its reference is what the fan quotes if anything goes wrong.
 *
 * @param receipt the M-Pesa receipt number once paid, otherwise null
 * @param detail  a few words on why it didn't go through, otherwise empty
 */
record Order(String reference, String eventId, String type, int quantity, String buyer, String phone,
             Money amount, Status status, String receipt, String detail) {

    enum Status {
        /** Its places are held while the fan pays. */
        AWAITING_PAYMENT,
        /** Paid: its tickets are issued. */
        PAID,
        /** The fan pressed Cancel on the M-Pesa prompt. Its places are free again. */
        CANCELLED,
        /** M-Pesa said no: wrong PIN, not enough money, phone off. Its places are free again. */
        FAILED,
        /** Not paid in time. Its places are free again. */
        EXPIRED,
        /** Money came in, but something about it is wrong. Its places stay held until a person looks. */
        FLAGGED
    }

    /**
     * "0712 ••• 678": enough for the fan to recognise their number, not enough to
     * copy it. Public, because a template can only call public methods.
     */
    public String maskedPhone() {
        return "0" + phone.substring(3, 6) + " ••• " + phone.substring(9);
    }

    public boolean waiting() {
        return status == Status.AWAITING_PAYMENT;
    }
}
