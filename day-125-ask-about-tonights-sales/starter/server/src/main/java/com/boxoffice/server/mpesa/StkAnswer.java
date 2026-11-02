package com.boxoffice.server.mpesa;

/**
 * Daraja's "OK, I've sent the prompt". Not "paid": that comes later, in the callback.
 *
 * @param checkoutRequestId how the callback will name this payment. Keep it: it's how we match the two up.
 */
public record StkAnswer(String checkoutRequestId, String merchantRequestId) {
}
