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
                    tickets.add(ticketFrom(row));
                }
            }
            return tickets;
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the tickets for " + eventId, e);
        }
    }

    /** The ticket with this code, if the event sold one. */
    public Optional<Ticket> findTicket(String eventId, String code) {
        try (PreparedStatement select = connection.prepareStatement(
                "select code, type_code, buyer, buyer_phone, price_cents, sold_at from tickets"
                        + " where event_id = ? and code = ?")) {
            select.setString(1, eventId);
            select.setString(2, code);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? Optional.of(ticketFrom(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't look up ticket " + code, e);
        }
    }

    /**
     * Marks the ticket as through the gate, unless it already is. True if this
     * call marked it; false if it was already in. One statement does the check
     * and the change together, so two scans can't both win.
     */
    public boolean markCheckedIn(String eventId, String code, Instant at) {
        try (PreparedStatement update = connection.prepareStatement(
                "update tickets set checked_in_at = ?"
                        + " where event_id = ? and code = ? and checked_in_at is null")) {
            update.setString(1, at.toString());
            update.setString(2, eventId);
            update.setString(3, code);
            return update.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("Couldn't check in ticket " + code, e);
        }
    }

    /** When the ticket came through the gate, or empty if it hasn't. */
    public Optional<Instant> checkedInAt(String eventId, String code) {
        try (PreparedStatement select = connection.prepareStatement(
                "select checked_in_at from tickets where event_id = ? and code = ?")) {
            select.setString(1, eventId);
            select.setString(2, code);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next() || row.getString(1) == null) {
                    return Optional.empty();
                }
                return Optional.of(Instant.parse(row.getString(1)));
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't read when " + code + " came in", e);
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

    private static Ticket ticketFrom(ResultSet row) throws SQLException {
        return new Ticket(
                row.getString("code"),
                row.getString("type_code"),
                row.getString("buyer"),
                row.getString("buyer_phone"),
                Money.ofCents(row.getLong("price_cents")),
                Instant.parse(row.getString("sold_at")));
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
