package com.boxoffice.server;

import com.boxoffice.core.Money;

/** An order placed on the site. Its reference is what the fan quotes if anything goes wrong. */
record Order(String reference, String eventId, String type, int quantity, String buyer, String phone,
             Money amount, Status status) {

    enum Status {
        /** Its places are held while the fan pays. */
        AWAITING_PAYMENT
    }

    /**
     * "0712 ••• 678": enough for the fan to recognise their number, not enough to
     * copy it. Public, because a template can only call public methods.
     */
    public String maskedPhone() {
        return "0" + phone.substring(3, 6) + " ••• " + phone.substring(9);
    }
}
