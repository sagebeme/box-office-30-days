package com.boxoffice.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
    private Duration slowDisk = Duration.ZERO;

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

    /**
     * For practice only: makes every sale take this long to save, like a tired
     * USB stick on an old laptop. The app turns it on with --slow-disk.
     */
    public void simulateSlowDisk(Duration delay) {
        slowDisk = delay;
    }

    public void recordSale(String eventId, Ticket ticket) {
        pauseForSlowDisk();
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into tickets (event_id, code, type_code, buyer, buyer_phone, price_cents, sold_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, eventId);
            insert.setString(2, ticket.code());
            insert.setString(3, ticket.typeCode());
            insert.setString(4, ticket.buyer());
            insert.setString(5, ticket.phone());
            insert.setLong(6, ticket.price().cents());
            insert.setString(7, ticket.soldAt().toString());
            insert.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Couldn't record ticket " + ticket.code(), e);
        }
    }

    /** Every ticket sold for the event, oldest first. */
    public List<Ticket> tickets(String eventId) {
        try (PreparedStatement select = connection.prepareStatement(
                "select code, type_code, buyer, buyer_phone, price_cents, sold_at from tickets"
                        + " where event_id = ? order by rowid")) {
            select.setString(1, eventId);
            List<Ticket> tickets = new ArrayList<>();
            try (ResultSet row = select.executeQuery()) {
                while (row.next()) {
                    tickets.add(new Ticket(
                            row.getString("code"),
                            row.getString("type_code"),
                            row.getString("buyer"),
                            row.getString("buyer_phone"),
                            Money.ofCents(row.getLong("price_cents")),
                            Instant.parse(row.getString("sold_at"))));
                }
            }
            return tickets;
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the tickets for " + eventId, e);
        }
    }

    /** How many tickets of one type the event has sold. */
    public int countSold(String eventId, String typeCode) {
        try (PreparedStatement count = connection.prepareStatement(
                "select count(*) from tickets where event_id = ? and type_code = ?")) {
            count.setString(1, eventId);
            count.setString(2, typeCode);
            try (ResultSet row = count.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't count " + typeCode + " tickets", e);
        }
    }

    private void pauseForSlowDisk() {
        if (slowDisk.isZero()) {
            return;
        }
        try {
            Thread.sleep(slowDisk);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
