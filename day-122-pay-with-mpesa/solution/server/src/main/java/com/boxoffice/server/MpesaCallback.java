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
        String expected = settings.callbackSecret();
        if (expected == null || expected.isBlank() || !Tokens.same(secret, expected)) {
            log.warn("M-Pesa callback with the wrong secret: ignored");
            return ACCEPTED;
        }
        try {
            Payments.Outcome outcome = payments.settle(StkCallback.parse(body));
            log.info("M-Pesa callback: {}", outcome);
        } catch (IllegalArgumentException e) {
            log.warn("M-Pesa callback we can't read: ignored ({})", e.getMessage());
        } catch (RuntimeException e) {
            // Our own failure, like the database being down. Daraja redelivers; the reconciler is the safety net.
            log.error("M-Pesa callback failed on our side", e);
        }
        return ACCEPTED;
    }
}
