package com.boxoffice.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;

/**
 * The gate's memory: an SQLite file on the laptop's disk. Close the app,
 * pull the plug, open it again tomorrow: what was saved is still there.
 */
public final class TicketStore implements AutoCloseable {

    private final Connection connection;

    private TicketStore(Connection connection) {
        this.connection = connection;
    }

    /** Opens the database file, making it if it isn't there, and brings its tables up to date. */
    public static TicketStore open(Path file) {
        String url = "jdbc:sqlite:" + file.toAbsolutePath();
        Flyway.configure().dataSource(url, null, null).load().migrate();
        try {
            Connection connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                // SQLite only checks "references" when asked to.
                statement.execute("pragma foreign_keys = on");
            }
            return new TicketStore(connection);
        } catch (SQLException e) {
            throw new StoreException("Couldn't open " + file, e);
        }
    }

    public void saveEvent(Event event) {
        try (PreparedStatement insertEvent = connection.prepareStatement(
                     "insert into events (id, name, starts_at, ends_at) values (?, ?, ?, ?)");
             PreparedStatement insertType = connection.prepareStatement(
                     "insert into ticket_types (event_id, code, name, price_cents, capacity, position)"
                             + " values (?, ?, ?, ?, ?, ?)")) {
            insertEvent.setString(1, event.id());
            insertEvent.setString(2, event.name());
            insertEvent.setString(3, event.startsAt().toInstant().toString());
            insertEvent.setString(4, event.endsAt().toInstant().toString());
            insertEvent.executeUpdate();

            int position = 0;
            for (TicketType type : event.ticketTypes()) {
                insertType.setString(1, event.id());
                insertType.setString(2, type.code());
                insertType.setString(3, type.name());
                insertType.setLong(4, type.price().cents());
                insertType.setInt(5, type.capacity());
                insertType.setInt(6, position++);
                insertType.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't save event " + event.id(), e);
        }
    }

    public Optional<Event> findEvent(String id) {
        try (PreparedStatement findEvent = connection.prepareStatement(
                     "select name, starts_at, ends_at from events where id = ?");
             PreparedStatement findTypes = connection.prepareStatement(
                     "select code, name, price_cents, capacity from ticket_types"
                             + " where event_id = ? order by position")) {
            findEvent.setString(1, id);
            try (ResultSet row = findEvent.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                findTypes.setString(1, id);
                List<TicketType> types = new ArrayList<>();
                try (ResultSet typeRow = findTypes.executeQuery()) {
                    while (typeRow.next()) {
                        types.add(new TicketType(
                                typeRow.getString("code"),
                                typeRow.getString("name"),
                                Money.ofCents(typeRow.getLong("price_cents")),
                                typeRow.getInt("capacity")));
                    }
                }
                return Optional.of(new Event(id, row.getString("name"),
                        Instant.parse(row.getString("starts_at")).atZone(NairobiTime.ZONE),
                        Instant.parse(row.getString("ends_at")).atZone(NairobiTime.ZONE),
                        types));
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't read event " + id, e);
        }
    }

    public void recordSale(String eventId, Ticket ticket) {
        // TODO: insert one row into tickets, the same way saveEvent inserts into events:
        //   - prepare "insert into tickets (event_id, code, type_code, buyer, price_cents, sold_at)
        //              values (?, ?, ?, ?, ?, ?)" inside try ( ... )
        //   - fill each ? in order: setString for text, setLong for the price in cents,
        //     and ticket.soldAt().toString() for the time
        //   - executeUpdate()
        //   - catch SQLException and throw new StoreException("Couldn't record ticket " + ticket.code(), e)
        throw new UnsupportedOperationException("Not written yet: Day 104, step 4");
    }

    /** Every ticket sold for the event, oldest first. */
    public List<Ticket> tickets(String eventId) {
        // TODO: select code, type_code, buyer, price_cents, sold_at from tickets
        //       where event_id = ? order by rowid   (rowid is the order rows went in)
        // TODO: for each row, make a Ticket. Money.ofCents(row.getLong("price_cents")) for the price,
        //       Instant.parse(row.getString("sold_at")) for the time. findEvent shows the pattern.
        // TODO: catch SQLException -> StoreException("Couldn't read the tickets for " + eventId, e)
        throw new UnsupportedOperationException("Not written yet: Day 104, step 5");
    }

    /** How many tickets of one type the event has sold. */
    public int countSold(String eventId, String typeCode) {
        // TODO: "select count(*) from tickets where event_id = ? and type_code = ?"
        //       The answer is one row with one column: row.next(), then row.getInt(1).
        // TODO: catch SQLException -> StoreException("Couldn't count " + typeCode + " tickets", e)
        throw new UnsupportedOperationException("Not written yet: Day 104, step 6");
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StoreException("Couldn't close the database", e);
        }
    }
}
