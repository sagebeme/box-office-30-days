package com.boxoffice.server.mpesa;

/**
 * Safaricom's M-Pesa API, the one call the site needs. Two versions: the
 * real one (DarajaClient) for the sandbox and live, and StubDaraja for
 * practising with no Safaricom account at all.
 */
public interface Daraja {

    /**
     * Sends the "enter your M-Pesa PIN" prompt to the fan's phone.
     *
     * @throws DarajaException if M-Pesa refused or couldn't be reached
     */
    StkAnswer stkPush(StkRequest request);
}
