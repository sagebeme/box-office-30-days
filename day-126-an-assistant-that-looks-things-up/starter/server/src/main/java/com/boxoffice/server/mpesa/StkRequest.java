package com.boxoffice.server.mpesa;

import com.boxoffice.core.Money;

/**
 * One "please pay" prompt to a phone.
 *
 * @param phone            254712345678: 12 digits, no plus
 * @param amount           whole shillings only: M-Pesa has no cents
 * @param accountReference what the fan sees as the account, cut to 12 characters
 * @param description      a few words about the payment, cut to 13 characters
 */
public record StkRequest(String phone, Money amount, String accountReference, String description) {

    public StkRequest {
        if (phone == null || !phone.matches("254[17]\\d{8}")) {
            throw new IllegalArgumentException("M-Pesa wants a number like 254712345678, not " + phone);
        }
        // Refuse, don't round: rounding KES 1,500.50 down loses money, rounding up overcharges.
        if (amount.cents() % 100 != 0) {
            throw new IllegalArgumentException("M-Pesa takes whole shillings only, not " + amount.format());
        }
        if (amount.cents() == 0) {
            throw new IllegalArgumentException("Nothing to pay");
        }
        // Daraja silently refuses longer values, so cut them here.
        accountReference = cut(accountReference, 12);
        description = cut(description, 13);
    }

    public long shillings() {
        return amount.cents() / 100;
    }

    private static String cut(String text, int most) {
        return text.length() <= most ? text : text.substring(0, most);
    }
}
