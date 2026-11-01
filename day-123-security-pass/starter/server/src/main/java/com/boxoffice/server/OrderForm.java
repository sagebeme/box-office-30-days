package com.boxoffice.server;

/**
 * The order form as the fan filled it in. Every field arrives as typed, so
 * any of them can be missing, empty or nonsense: Orders checks them all.
 */
record OrderForm(String type, Integer quantity, String buyer, String phone) {

    /** An empty form, for the page before anyone has typed anything. */
    static OrderForm blank() {
        return new OrderForm("REG", 1, "", "");
    }
}
