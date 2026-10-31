package com.boxoffice.server;

import com.boxoffice.server.mpesa.DarajaClient;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The boxoffice.mpesa.* settings from application.properties, which read
 * them from environment variables (MPESA_MODE, MPESA_CONSUMER_KEY...).
 *
 * @param mode           stub (practice, the default), sandbox or live
 * @param callbackBase   this server's public https address, like https://tickets.example.co.ke
 * @param callbackSecret the long random last part of our callback address
 * @param callbackIps    Day 123: only callbacks from these addresses count. Empty: any address
 */
@ConfigurationProperties("boxoffice.mpesa")
record MpesaSettings(String mode, String consumerKey, String consumerSecret, String passkey, String shortcode,
                     String transactionType, String callbackBase, String callbackSecret,
                     List<String> callbackIps) {

    MpesaSettings {
        // Day 123: the addresses Safaricom's callbacks come from. Empty: don't check.
        callbackIps = callbackIps == null ? List.of()
                : callbackIps.stream().map(String::strip).filter(ip -> !ip.isEmpty()).toList();
    }

    boolean stub() {
        return "stub".equals(mode);
    }

    DarajaClient.Account account() {
        return new DarajaClient.Account(consumerKey, consumerSecret, shortcode, passkey, transactionType);
    }

    /**
     * Where Safaricom posts the result. The secret at the end is the only way
     * we know a callback came from someone who saw our STK push: Daraja
     * doesn't sign its callbacks.
     */
    URI callbackUrl() {
        return URI.create(callbackBase.replaceAll("/+$", "") + "/mpesa/callback/" + callbackSecret);
    }

    @Override
    public String toString() {
        return "MpesaSettings[mode=" + mode + ", shortcode=" + shortcode + ", callbackBase=" + callbackBase
                + ", secrets hidden]";
    }
}
