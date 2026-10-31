package com.boxoffice.server;

import com.boxoffice.server.mpesa.StkCallback;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The "Pretend to pay" and "Pretend to cancel" buttons, in practice mode only
 * (MPESA_MODE=stub). They build the callback Safaricom would send and settle
 * it exactly as a real one, so the whole flow runs with no Safaricom account.
 *
 * <p>@ConditionalOnProperty: in sandbox or live mode this controller isn't
 * created at all, so these addresses don't exist.
 */
@Controller
@ConditionalOnProperty(name = "boxoffice.mpesa.mode", havingValue = "stub")
class PracticePayments {

    private final Payments payments;
    private final Orders orders;

    PracticePayments(Payments payments, Orders orders) {
        this.payments = payments;
        this.orders = orders;
    }

    @PostMapping("/practice/orders/{reference}/pay")
    String pretend(@PathVariable String reference, @RequestParam(defaultValue = "0") int result, Model model,
                   HttpServletResponse response) {
        var order = orders.find(reference);
        var checkout = payments.latestCheckout(reference);
        if (order.isEmpty() || checkout.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", "No payment asked for on order " + reference);
            return "not-found";
        }
        payments.settle(StkCallback.parse(callback(checkout.get(), result, order.get())));
        model.addAttribute("order", orders.find(reference).orElseThrow());
        model.addAttribute("tickets", payments.tickets(reference));
        model.addAttribute("practice", true);
        return "order :: status";
    }

    /** The JSON Safaricom posts, for a payment that went through (0) or didn't (1032 cancelled, 2001 wrong PIN...). */
    static String callback(String checkoutRequestId, int result, Order order) {
        if (result != 0) {
            return """
                    {"Body":{"stkCallback":{"MerchantRequestID":"practice","CheckoutRequestID":"%s",\
                    "ResultCode":%d,"ResultDesc":"Request cancelled by user"}}}""".formatted(checkoutRequestId, result);
        }
        return """
                {"Body":{"stkCallback":{"MerchantRequestID":"practice","CheckoutRequestID":"%s",\
                "ResultCode":0,"ResultDesc":"The service request is processed successfully.",\
                "CallbackMetadata":{"Item":[{"Name":"Amount","Value":%d},\
                {"Name":"MpesaReceiptNumber","Value":"PRACTICE%02d"},{"Name":"Balance"},\
                {"Name":"TransactionDate","Value":20261114190000},{"Name":"PhoneNumber","Value":%s}]}}}}"""
                .formatted(checkoutRequestId, order.amount().cents() / 100, order.quantity(), order.phone());
    }
}
