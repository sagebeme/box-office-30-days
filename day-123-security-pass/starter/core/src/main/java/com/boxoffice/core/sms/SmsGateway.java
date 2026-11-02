package com.boxoffice.core.sms;

/**
 * Something that can send a text message. The real one talks to Africa's
 * Talking over the internet; FakeSms just remembers what it was asked to send,
 * for tests and for practising with no account.
 */
public interface SmsGateway {

    /** @param phone 2547XXXXXXXX, the way the gate stores it */
    SmsResult send(String phone, String message);
}
