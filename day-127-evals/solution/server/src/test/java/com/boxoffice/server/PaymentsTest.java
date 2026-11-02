package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.server.mpesa.StkCallback;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Settling orders from what Safaricom says, with the practice M-Pesa (MPESA_MODE=stub). */
@SpringBootTest
@ActiveProfiles("test")
class PaymentsTest {

    @Autowired
    JdbcClient db;

    @Autowired
    Orders orders;

    @Autowired
    Payments payments;

    @Autowired
    Places places;

    private Order order;
    private String checkout;

    @BeforeEach
    void otienoOrdersTwoRegularAndGetsThePrompt() {
        ServerTestSupport.emptyTheServer(db);
        order = ((Orders.Placed.Done) orders.place("kilele-2026",
                new OrderForm("REG", 2, "Otieno", "0712 345 678"))).order();
        assertThat(payments.requestPayment(order.reference())).isEmpty();
        checkout = payments.latestCheckout(order.reference()).orElseThrow();
    }

    private String paid(long shillings) {
        return """
                {"Body":{"stkCallback":{"CheckoutRequestID":"%s","ResultCode":0,"ResultDesc":"ok",\
                "CallbackMetadata":{"Item":[{"Name":"Amount","Value":%d},{"Name":"MpesaReceiptNumber","Value":"QK12ABC3DE"},\
                {"Name":"PhoneNumber","Value":254712345678}]}}}}""".formatted(checkout, shillings);
    }

    private String notPaid(int resultCode, String why) {
        return """
                {"Body":{"stkCallback":{"CheckoutRequestID":"%s","ResultCode":%d,"ResultDesc":"%s"}}}"""
                .formatted(checkout, resultCode, why);
    }

    private Order now() {
        return orders.find(order.reference()).orElseThrow();
    }

    private int regularTaken() {
        return places.taken("kilele-2026", "REG");
    }

    @Test
    void aPaymentForTheRightAmountIssuesTheTickets() {
        assertThat(payments.settle(StkCallback.parse(paid(3_000)))).isEqualTo(Payments.Outcome.PAID);

        assertThat(now().status()).isEqualTo(Order.Status.PAID);
        assertThat(now().receipt()).isEqualTo("QK12ABC3DE");
        assertThat(payments.tickets(order.reference())).containsExactly("W-REG-0001", "W-REG-0002");
        // The held places became tickets: still 2 taken, not 4.
        assertThat(regularTaken()).isEqualTo(2);
    }

    @Test
    void theNextPaidOrderCarriesOnTheNumbering() {
        payments.settle(StkCallback.parse(paid(3_000)));
        order = ((Orders.Placed.Done) orders.place("kilele-2026",
                new OrderForm("REG", 1, "Achieng", "0700 111 222"))).order();
        payments.requestPayment(order.reference());
        checkout = payments.latestCheckout(order.reference()).orElseThrow();

        payments.settle(StkCallback.parse(paid(1_500)));

        assertThat(payments.tickets(order.reference())).containsExactly("W-REG-0003");
    }

    @Test
    void theSameCallbackTwiceIssuesTheTicketsOnce() {
        payments.settle(StkCallback.parse(paid(3_000)));

        assertThat(payments.settle(StkCallback.parse(paid(3_000)))).isEqualTo(Payments.Outcome.ALREADY_SETTLED);
        assertThat(db.sql("select count(*) from tickets").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void aCancelledPaymentGivesThePlacesBack() {
        assertThat(payments.settle(StkCallback.parse(notPaid(1032, "Request cancelled by user"))))
                .isEqualTo(Payments.Outcome.CANCELLED);

        assertThat(now().status()).isEqualTo(Order.Status.CANCELLED);
        assertThat(now().detail()).isEqualTo("You cancelled the M-Pesa payment");
        assertThat(regularTaken()).isZero();
    }

    @Test
    void aFailedPaymentSaysWhatMpesaSaid() {
        assertThat(payments.settle(StkCallback.parse(notPaid(2001, "The initiator information is invalid."))))
                .isEqualTo(Payments.Outcome.FAILED);

        assertThat(now().detail()).isEqualTo("M-Pesa said: The initiator information is invalid.");
        assertThat(regularTaken()).isZero();
    }

    @Test
    void aWrongAmountIsFlaggedForAPersonAndIssuesNoTickets() {
        assertThat(payments.settle(StkCallback.parse(paid(1_500)))).isEqualTo(Payments.Outcome.FLAGGED);

        assertThat(now().status()).isEqualTo(Order.Status.FLAGGED);
        assertThat(payments.tickets(order.reference())).isEmpty();
        assertThat(payments.flagged()).containsExactly(
                order.reference() + ": Paid KES 1,500 for an order of KES 3,000");
        // Someone paid something: keep their places until a person has looked.
        assertThat(regularTaken()).isEqualTo(2);
    }

    @Test
    void aPaymentWithNoAmountIsFlagged() {
        String noAmount = """
                {"Body":{"stkCallback":{"CheckoutRequestID":"%s","ResultCode":0,"ResultDesc":"ok",\
                "CallbackMetadata":{"Item":[{"Name":"MpesaReceiptNumber","Value":"QK1"}]}}}}""".formatted(checkout);

        assertThat(payments.settle(StkCallback.parse(noAmount))).isEqualTo(Payments.Outcome.FLAGGED);
        assertThat(now().detail()).isEqualTo("M-Pesa didn't say how much was paid");
    }

    @Test
    void aCallbackForAPaymentWeNeverAskedForChangesNothing() {
        checkout = "ws_CO_SOMEONE_ELSES";

        assertThat(payments.settle(StkCallback.parse(paid(3_000)))).isEqualTo(Payments.Outcome.UNKNOWN_PAYMENT);
        assertThat(now().status()).isEqualTo(Order.Status.AWAITING_PAYMENT);
    }

    @Test
    void theFirstPromptCanStillBePaidAfterASecondOneWasSent() {
        String first = checkout;
        payments.requestPayment(order.reference());
        checkout = first;

        assertThat(payments.settle(StkCallback.parse(paid(3_000)))).isEqualTo(Payments.Outcome.PAID);
    }

    @Test
    void anOrderNotPaidInTenMinutesRunsOutAndFreesItsPlaces() {
        assertThat(payments.expireOrders(Instant.now().plus(Duration.ofMinutes(9)))).isZero();
        assertThat(payments.expireOrders(Instant.now().plus(Duration.ofMinutes(11)))).isEqualTo(1);

        assertThat(now().status()).isEqualTo(Order.Status.EXPIRED);
        assertThat(regularTaken()).isZero();
    }

    @Test
    void aPaymentAfterTheOrderRanOutIsFlaggedNotLost() {
        payments.expireOrders(Instant.now().plus(Duration.ofMinutes(11)));

        assertThat(payments.settle(StkCallback.parse(paid(3_000)))).isEqualTo(Payments.Outcome.FLAGGED);
        assertThat(now().detail()).isEqualTo("Paid after the order ran out");
    }

    @Test
    void aSettledOrderCantBePromptedAgain() {
        payments.settle(StkCallback.parse(paid(3_000)));

        assertThat(payments.requestPayment(order.reference())).contains("This order isn't waiting for payment");
    }
}
