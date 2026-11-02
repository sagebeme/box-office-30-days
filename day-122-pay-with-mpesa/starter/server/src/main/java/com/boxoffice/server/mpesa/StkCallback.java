package com.boxoffice.server.mpesa;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What Safaricom posts to our callback URL once the fan has paid, cancelled
 * or failed:
 *
 * <pre>
 * {"Body":{"stkCallback":{
 *   "MerchantRequestID":"...", "CheckoutRequestID":"ws_CO_...",
 *   "ResultCode":0, "ResultDesc":"The service request is processed successfully.",
 *   "CallbackMetadata":{"Item":[
 *     {"Name":"Amount","Value":3000}, {"Name":"MpesaReceiptNumber","Value":"QK12ABC3DE"},
 *     {"Name":"TransactionDate","Value":20261114183512}, {"Name":"PhoneNumber","Value":254712345678}]}}}}
 * </pre>
 *
 * Only a success has CallbackMetadata. The items come in any order, and one
 * may have no Value at all.
 *
 * @param amount empty if M-Pesa didn't say, or said something that isn't whole shillings
 */
public record StkCallback(String checkoutRequestId, int resultCode, String resultDesc,
                          Optional<Long> amount, Optional<String> receipt, Optional<String> phone) {

    /** The fan pressed Cancel on the prompt. */
    public static final int CANCELLED_BY_USER = 1032;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public boolean paid() {
        return resultCode == 0;
    }

    /**
     * Reads a callback body.
     *
     * @throws IllegalArgumentException if it isn't one
     */
    public static StkCallback parse(String body) {
        JsonNode root = readJson(body);
        // Some senders wrap the JSON in a string: "{\"Body\":...}". Unwrap it once.
        if (root.isString()) {
            root = readJson(root.asString());
        }
        JsonNode callback = root.path("Body").path("stkCallback");
        String checkoutRequestId = callback.path("CheckoutRequestID").asString("");
        JsonNode resultCode = callback.path("ResultCode");
        if (checkoutRequestId.isBlank() || resultCode.isMissingNode() || resultCode.isNull()) {
            throw new IllegalArgumentException("Not an STK callback");
        }
        JsonNode items = callback.path("CallbackMetadata").path("Item");
        return new StkCallback(
                checkoutRequestId,
                // A number from live, sometimes a string from the sandbox: "0".
                resultCode.isNumber() ? resultCode.asInt() : Integer.parseInt(resultCode.asString().strip()),
                callback.path("ResultDesc").asString(""),
                item(items, "Amount").flatMap(StkCallback::wholeShillings),
                item(items, "MpesaReceiptNumber").map(JsonNode::asString),
                // PhoneNumber is a number in the JSON: 254712345678, not "254712345678".
                item(items, "PhoneNumber").map(value -> value.isNumber() ? String.valueOf(value.asLong()) : value.asString()));
    }

    /** The Value of the item called name, looked up by name (any case), wherever it is in the list. */
    static Optional<JsonNode> item(JsonNode items, String name) {
        // TODO Day 122, step 4: find the item whose "Name" is name, in any case
        //   ("amount" and "AMOUNT" are "Amount"), and return its "Value".
        //   - items.values() is every item in the list, in whatever order they came.
        //   - item.path("Name").asString("") is its name ("" if it has none).
        //   - No such item, or one with no Value (isMissingNode()) or a null one (isNull()): Optional.empty().
        // Compare with toLowerCase(Locale.ROOT), so a Turkish laptop doesn't turn "i" into "ı".
        throw new UnsupportedOperationException("Not written yet: Day 122, step 4");
    }

    /** 3000 and 3000.00 are 3000 shillings. 3000.50 isn't something M-Pesa sends, so it's no amount at all. */
    private static Optional<Long> wholeShillings(JsonNode value) {
        try {
            BigDecimal amount = new BigDecimal(value.asString().strip());
            return amount.stripTrailingZeros().scale() <= 0 ? Optional.of(amount.longValueExact()) : Optional.empty();
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    private static JsonNode readJson(String body) {
        try {
            return JSON.readTree(body == null ? "" : body);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Not JSON", e);
        }
    }
}
