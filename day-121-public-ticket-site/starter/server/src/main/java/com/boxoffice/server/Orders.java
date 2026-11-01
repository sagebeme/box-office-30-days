package com.boxoffice.server;

import com.boxoffice.core.Money;
import com.boxoffice.core.PhoneNumbers;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orders from the site: checks the form, holds the places, keeps the order. */
@Service
class Orders {

    /** Most tickets one order can hold. A tout can't take all 50 VIP places in one go. */
    static final int MOST_PER_ORDER = 6;

    /** What happened to an order: placed, or refused with a message for each field that's wrong. */
    sealed interface Placed {

        record Done(Order order) implements Placed {
        }

        /** Field name ("buyer", "phone", "quantity", "type") to what the fan should fix. */
        record Refused(Map<String, String> problems) implements Placed {
        }
    }

    private final JdbcClient db;
    private final Places places;
    private final Clock clock;

    Orders(JdbcClient db, Places places, Clock clock) {
        this.db = db;
        this.places = places;
        this.clock = clock;
    }

    @Transactional
    Placed place(String eventId, OrderForm form) {
        Map<String, String> problems = new LinkedHashMap<>();
        // TODO Day 121, step 3: check the form, putting a message in problems for each field that's wrong:
        //   buyer, stripped:  empty -> "Type the name for the tickets"
        //                     longer than 60 -> "Keep the name under 60 letters"
        //   phone:            PhoneNumbers.normalise(form.phone()) is empty
        //                     -> "Type a Kenyan mobile number, like 0712 345 678"
        //   quantity:         null, below 1 or above MOST_PER_ORDER -> "Choose 1 to 6 tickets"
        // Check every field before returning, so the fan sees everything to fix at once.
        // If problems isn't empty, return new Placed.Refused(problems).
        if (true) {
            throw new UnsupportedOperationException("Not written yet: Day 121, step 3");
        }
        String buyer = form.buyer().strip();
        Optional<String> phone = PhoneNumbers.normalise(form.phone());
        Integer quantity = form.quantity();

        // Lock the type's row first (Day 120), so two fans can't both take the last place.
        Optional<Integer> capacity = form.type() == null ? Optional.empty() : places.lockCapacity(eventId, form.type());
        if (capacity.isEmpty()) {
            return new Placed.Refused(Map.of("type", "Choose a ticket type"));
        }
        int left = places.left(capacity.get(), places.taken(eventId, form.type()));
        if (quantity > left) {
            return new Placed.Refused(Map.of("quantity", left == 0
                    ? "Sold out, sorry"
                    : "Only " + left + (left == 1 ? " place is" : " places are") + " left"));
        }

        long priceCents = db.sql("select price_cents from ticket_types where event_id = ? and code = ?")
                .params(eventId, form.type())
                .query(Long.class)
                .single();
        Order order = new Order(References.next(), eventId, form.type(), quantity, buyer, phone.get(),
                Money.ofCents(priceCents).times(quantity), Order.Status.AWAITING_PAYMENT);
        db.sql("insert into orders (reference, event_id, type_code, quantity, buyer, phone, amount_cents, status,"
                        + " created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(order.reference(), eventId, order.type(), order.quantity(), order.buyer(), order.phone(),
                        order.amount().cents(), order.status().name(),
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        return new Placed.Done(order);
    }

    Optional<Order> find(String reference) {
        return db.sql("select * from orders where reference = ?")
                .param(reference)
                .query((row, n) -> new Order(row.getString("reference"), row.getString("event_id"),
                        row.getString("type_code"), row.getInt("quantity"), row.getString("buyer"),
                        row.getString("phone"), Money.ofCents(row.getLong("amount_cents")),
                        Order.Status.valueOf(row.getString("status"))))
                .optional();
    }
}
