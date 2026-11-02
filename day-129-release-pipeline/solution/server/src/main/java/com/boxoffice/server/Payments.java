package com.boxoffice.server;

import com.boxoffice.core.Money;
import com.boxoffice.server.mpesa.Daraja;
import com.boxoffice.server.mpesa.DarajaException;
import com.boxoffice.server.mpesa.StkAnswer;
import com.boxoffice.server.mpesa.StkCallback;
import com.boxoffice.server.mpesa.StkRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asks M-Pesa for the money, and settles each order when Safaricom says what happened. */
@Service
class Payments {

    /** The site's "gate id": tickets paid for online are W-REG-0001, W-VIP-0001... */
    static final String WEB = "W";

    /** How long an order holds its places while the fan pays. */
    static final Duration HOLD = Duration.ofMinutes(10);

    /** What a callback did, for the log and the tests. */
    enum Outcome { PAID, CANCELLED, FAILED, FLAGGED, ALREADY_SETTLED, UNKNOWN_PAYMENT }

    private static final Logger log = LoggerFactory.getLogger(Payments.class);

    private final JdbcClient db;
    private final Orders orders;
    private final Places places;
    private final Daraja daraja;
    private final Limits limits;
    private final Clock clock;

    Payments(JdbcClient db, Orders orders, Places places, Daraja daraja, Limits limits, Clock clock) {
        this.db = db;
        this.orders = orders;
        this.places = places;
        this.daraja = daraja;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Sends the "enter your PIN" prompt for an order still awaiting payment.
     * Not @Transactional on purpose: never hold the database open while
     * waiting up to 15 seconds for Safaricom.
     *
     * @return what went wrong, fit to show the fan, or empty if the prompt is on its way
     */
    Optional<String> requestPayment(String reference) {
        Optional<Order> found = orders.find(reference);
        if (found.isEmpty() || !found.get().waiting()) {
            return Optional.of("This order isn't waiting for payment");
        }
        Order order = found.get();
        // Day 123: a few prompts per phone per 10 minutes, whoever asks.
        RateLimiter.Decision decision = limits.prompts().tryTake(order.phone());
        if (!decision.allowed()) {
            long minutes = (decision.retryAfterSeconds() + 59) / 60;
            return Optional.of("Too many M-Pesa prompts to this number. Try again in " + minutes
                    + (minutes == 1 ? " minute" : " minutes"));
        }
        try {
            StkAnswer answer = daraja.stkPush(new StkRequest(order.phone(), order.amount(),
                    order.reference().replace("-", ""), "Kilele tix"));
            db.sql("insert into stk_requests (checkout_request_id, order_reference, created_at) values (?, ?, ?)")
                    .params(answer.checkoutRequestId(), reference, now())
                    .update();
            return Optional.empty();
        } catch (DarajaException e) {
            log.warn("STK push for order {} failed: {}", reference, e.getMessage());
            return Optional.of(e.getMessage());
        }
    }

    /**
     * Settles the order a callback is about. Safe to call twice with the same
     * callback: Daraja redelivers, and only an order still waiting is changed.
     */
    @Transactional
    Outcome settle(StkCallback callback) {
        Optional<String> reference = db.sql("select order_reference from stk_requests where checkout_request_id = ?")
                .param(callback.checkoutRequestId())
                .query(String.class)
                .optional();
        if (reference.isEmpty()) {
            log.warn("M-Pesa callback for a payment we never asked for: {}", callback.checkoutRequestId());
            return Outcome.UNKNOWN_PAYMENT;
        }
        // Lock the order's row: two deliveries of one callback at once settle it once.
        Order.Status status = Order.Status.valueOf(db.sql("select status from orders where reference = ? for update")
                .param(reference.get())
                .query(String.class)
                .single());
        Order order = orders.find(reference.get()).orElseThrow();

        if (!callback.paid()) {
            if (status != Order.Status.AWAITING_PAYMENT) {
                return Outcome.ALREADY_SETTLED;
            }
            boolean cancelled = callback.resultCode() == StkCallback.CANCELLED_BY_USER;
            mark(order, cancelled ? Order.Status.CANCELLED : Order.Status.FAILED, null,
                    cancelled ? "You cancelled the M-Pesa payment" : "M-Pesa said: " + callback.resultDesc());
            return cancelled ? Outcome.CANCELLED : Outcome.FAILED;
        }

        String receipt = callback.receipt().orElse(null);
        if (status == Order.Status.EXPIRED) {
            // The fan paid after we gave the places back. They have paid: a person must sort it out.
            return flag(order, receipt, "Paid after the order ran out");
        }
        if (status != Order.Status.AWAITING_PAYMENT) {
            return Outcome.ALREADY_SETTLED;
        }
        Optional<Long> paid = callback.amount();
        if (paid.isEmpty()) {
            return flag(order, receipt, "M-Pesa didn't say how much was paid");
        }
        if (!Money.ofKes(paid.get()).equals(order.amount())) {
            return flag(order, receipt, "Paid " + Money.ofKes(paid.get()).format() + " for an order of "
                    + order.amount().format());
        }
        mark(order, Order.Status.PAID, receipt, "");
        issueTickets(order);
        log.info("Order {} paid, M-Pesa receipt {}", order.reference(), receipt);
        return Outcome.PAID;
    }

    /**
     * Gives up on orders not paid within HOLD, so their places go back on sale.
     * Run every minute by the Reconciler.
     *
     * @return how many orders ran out
     */
    int expireOrders(Instant now) {
        return db.sql("update orders set status = 'EXPIRED', detail = 'Not paid within 10 minutes'"
                        + " where status = 'AWAITING_PAYMENT' and created_at < ?")
                .param(OffsetDateTime.ofInstant(now.minus(HOLD), ZoneOffset.UTC))
                .update();
    }

    /** Orders where money came in but something is wrong. A person has to look at each one. */
    List<String> flagged() {
        return db.sql("select reference || ': ' || detail from orders where status = 'FLAGGED' order by created_at")
                .query(String.class)
                .list();
    }

    /** The codes of an order's tickets, once it's paid. */
    List<String> tickets(String reference) {
        return db.sql("select code from tickets where order_reference = ? order by code")
                .param(reference)
                .query(String.class)
                .list();
    }

    /** The newest prompt sent for an order: what the practice "Pretend to pay" button answers. */
    Optional<String> latestCheckout(String reference) {
        return db.sql("select checkout_request_id from stk_requests where order_reference = ?"
                        + " order by created_at desc limit 1")
                .param(reference)
                .query(String.class)
                .optional();
    }

    private Outcome flag(Order order, String receipt, String why) {
        mark(order, Order.Status.FLAGGED, receipt, why);
        // Loud on purpose: someone has paid and has no ticket yet.
        log.error("Order {} needs a person: {} (M-Pesa receipt {})", order.reference(), why, receipt);
        return Outcome.FLAGGED;
    }

    private void mark(Order order, Order.Status status, String receipt, String detail) {
        db.sql("update orders set status = ?, receipt = ?, detail = ? where reference = ?")
                .params(status.name(), receipt, detail, order.reference())
                .update();
    }

    /** W-REG-0001, W-REG-0002...: numbered on from the site's last ticket of that type. */
    private void issueTickets(Order order) {
        // Lock the type (Day 120) so two orders paid at once don't both take number 0007.
        places.lockCapacity(order.eventId(), order.type());
        String prefix = WEB + "-" + order.type() + "-";
        int issued = db.sql("select count(*) from tickets where event_id = ? and code like ?")
                .params(order.eventId(), prefix + "%")
                .query(Integer.class)
                .single();
        long priceCents = order.amount().cents() / order.quantity();
        for (int i = 1; i <= order.quantity(); i++) {
            db.sql("insert into tickets (event_id, code, order_reference, type_code, buyer, phone, price_cents, sold_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(order.eventId(), prefix + "%04d".formatted(issued + i), order.reference(), order.type(),
                            order.buyer(), order.phone(), priceCents, clock.instant().toString())
                    .update();
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
