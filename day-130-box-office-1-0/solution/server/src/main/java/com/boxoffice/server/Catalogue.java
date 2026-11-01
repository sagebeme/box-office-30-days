package com.boxoffice.server;

import com.boxoffice.core.Money;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** What the site shows: the event, and each ticket type with its price and places left. */
@Repository
class Catalogue {

    /** One kind of ticket as a fan sees it. */
    record Offer(String code, String name, Money price, int left) {

        public boolean soldOut() {
            return left == 0;
        }
    }

    record EventInfo(String id, String name, String venue, OffsetDateTime startsAt, List<Offer> offers) {

        Optional<Offer> offer(String code) {
            return offers.stream().filter(offer -> offer.code().equals(code)).findFirst();
        }
    }

    private final JdbcClient db;
    private final Places places;

    Catalogue(JdbcClient db, Places places) {
        this.db = db;
        this.places = places;
    }

    Optional<EventInfo> event(String id) {
        return db.sql("select id, name, venue, starts_at from events where id = ?")
                .param(id)
                .query((row, n) -> new EventInfo(row.getString("id"), row.getString("name"),
                        row.getString("venue"), row.getObject("starts_at", OffsetDateTime.class), offers(id)))
                .optional();
    }

    private List<Offer> offers(String eventId) {
        record Type(String code, String name, long priceCents, int capacity) {
        }
        List<Type> types = db.sql("select code, name, price_cents, capacity from ticket_types"
                        + " where event_id = ? order by price_cents")
                .param(eventId)
                .query((row, n) -> new Type(row.getString("code"), row.getString("name"),
                        row.getLong("price_cents"), row.getInt("capacity")))
                .list();
        return types.stream()
                .map(type -> new Offer(type.code(), type.name(), Money.ofCents(type.priceCents()),
                        places.left(type.capacity(), places.taken(eventId, type.code()))))
                .toList();
    }
}
