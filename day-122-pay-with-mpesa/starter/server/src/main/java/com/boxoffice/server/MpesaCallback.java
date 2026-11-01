package com.boxoffice.server;

import com.boxoffice.server.mpesa.StkCallback;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Safaricom posts each payment's result:
 * {@code POST /mpesa/callback/<our secret>}.
 *
 * <p>It <b>always</b> answers 200 {"ResultCode":0,"ResultDesc":"Accepted"},
 * whatever happened:
 * <ul>
 *   <li>Anything else makes Safaricom send the callback again and again.</li>
 *   <li>A different answer for a wrong secret would tell a stranger probing
 *       the address that they'd found something.</li>
 * </ul>
 * So problems go to the log, loudly, never into the answer.
 */
@RestController
class MpesaCallback {

    static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private static final Logger log = LoggerFactory.getLogger(MpesaCallback.class);

    private final Payments payments;
    private final MpesaSettings settings;

    MpesaCallback(Payments payments, MpesaSettings settings) {
        this.payments = payments;
        this.settings = settings;
    }

    @PostMapping("/mpesa/callback/{secret}")
    Map<String, Object> receive(@PathVariable String secret, @RequestBody(required = false) String body) {
        // TODO Day 122, step 6: settle the payment, and answer ACCEPTED whatever happens.
        //   1. Compare secret with settings.callbackSecret() using Tokens.same (never equals: timing).
        //      Blank or different: log.warn(...) and return ACCEPTED, having done nothing.
        //   2. payments.settle(StkCallback.parse(body)), and log.info the Outcome.
        //   3. Catch IllegalArgumentException (not a callback): log.warn.
        //      Catch any other RuntimeException (our own failure): log.error with the exception.
        //   4. Return ACCEPTED.
        throw new UnsupportedOperationException("Not written yet: Day 122, step 6");
    }
}
