package com.boxoffice.core.sms;

/** What happened to one text message. detail says why, when it wasn't sent. */
public record SmsResult(String phone, boolean sent, String detail) {

    public static SmsResult sent(String phone) {
        return new SmsResult(phone, true, "Sent");
    }

    public static SmsResult failed(String phone, String why) {
        return new SmsResult(phone, false, why);
    }
}
