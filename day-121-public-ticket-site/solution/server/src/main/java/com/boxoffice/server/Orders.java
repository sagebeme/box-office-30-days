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
        String buyer = form.buyer() == null ? "" : form.buyer().strip();
        if (buyer.isEmpty()) {
            problems.put("buyer", "Type the name for the tickets");
        } else if (buyer.length() > 60) {
            problems.put("buyer", "Keep the name under 60 letters");
        }
        Optional<String> phone = PhoneNumbers.normalise(form.phone());
        if (phone.isEmpty()) {
            problems.put("phone", "Type a Kenyan mobile number, like 0712 345 678");
        }
        Integer quantity = form.quantity();
        if (quantity == null || quantity < 1 || quantity > MOST_PER_ORDER) {
            problems.put("quantity", "Choose 1 to " + MOST_PER_ORDER + " tickets");
        }
        if (!problems.isEmpty()) {
            return new Placed.Refused(problems);
        }

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
