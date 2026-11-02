package com.boxoffice.core;

import com.boxoffice.core.sync.OutboxEntry;
import com.boxoffice.core.sync.SalePayload;
import com.boxoffice.core.sync.SyncConflict;
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
import java.util.UUID;
import org.flywaydb.core.Flyway;

/**
 * The gate's memory: an SQLite file on the laptop's disk. Close the app,
 * pull the plug, open it again tomorrow: what was saved is still there.
 */
public final class TicketStore implements AutoCloseable {

    private final Connection connection;
    private Runnable afterEachTicket = () -> { };
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
                // Write-ahead log: a change goes to gate.db-wal first and joins gate.db later,
                // so a crash mid-write never leaves gate.db itself half-changed.
                statement.execute("pragma journal_mode = wal");
                // Wait for the disk to confirm every commit. Slower, but a commit that
                // returned is still there after the power goes out.
                statement.execute("pragma synchronous = full");
            }
            return new TicketStore(connection);
        } catch (SQLException e) {
            throw new StoreException("Couldn't open " + file, e);
        }
    }

    public synchronized void saveEvent(Event event) {
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

    /** Saves one ticket, and its note in the outbox, in one transaction. */
    public void recordSale(String eventId, Ticket ticket) {
        pauseForSlowDisk();
        try {
            inTransaction(() -> {
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
                }
                queueForSync(eventId, List.of(ticket));
            });
        } catch (SQLException e) {
            throw new StoreException("Couldn't record ticket " + ticket.code(), e);
        }
    }

    /**
     * Saves a group sale: one row in sales, and one ticket per person. All of
     * it or none of it: if anything fails halfway (or the power goes), the
     * whole sale is undone.
     */
    public void recordGroupSale(String eventId, List<Ticket> tickets) {
        Ticket first = tickets.getFirst();
        Money total = Money.ZERO;
        for (Ticket ticket : tickets) {
            total = total.plus(ticket.price());
        }
        Money saleTotal = total;
        try {
            inTransaction(() -> {
                long saleId;
                try (PreparedStatement insertSale = connection.prepareStatement(
                        "insert into sales (event_id, type_code, buyer, quantity, total_cents, sold_at)"
                                + " values (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    insertSale.setString(1, eventId);
                    insertSale.setString(2, first.typeCode());
                    insertSale.setString(3, first.buyer());
                    insertSale.setInt(4, tickets.size());
                    insertSale.setLong(5, saleTotal.cents());
                    insertSale.setString(6, first.soldAt().toString());
                    insertSale.executeUpdate();
                    try (ResultSet keys = insertSale.getGeneratedKeys()) {
                        keys.next();
                        saleId = keys.getLong(1);
                    }
                }
                try (PreparedStatement insertTicket = connection.prepareStatement(
                        "insert into tickets (event_id, code, type_code, buyer, buyer_phone, price_cents, sold_at, sale_id)"
                                + " values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (Ticket ticket : tickets) {
                        insertTicket.setString(1, eventId);
                        insertTicket.setString(2, ticket.code());
                        insertTicket.setString(3, ticket.typeCode());
                        insertTicket.setString(4, ticket.buyer());
                        insertTicket.setString(5, ticket.phone());
                        insertTicket.setLong(6, ticket.price().cents());
                        insertTicket.setString(7, ticket.soldAt().toString());
                        insertTicket.setLong(8, saleId);
                        insertTicket.executeUpdate();
                        afterEachTicket.run();
                    }
                }
                queueForSync(eventId, tickets);
            });
        } catch (SQLException e) {
            throw new StoreException("Couldn't record the sale to " + first.buyer(), e);
        }
    }

    /**
     * Day 116: leaves a note in the outbox for the sync server. Called inside
     * the sale's transaction, so the note and the sale are saved together.
     */
    private void queueForSync(String eventId, List<Ticket> tickets) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into outbox (event_id, idempotency_key, payload, created_at) values (?, ?, ?, ?)")) {
            insert.setString(1, eventId);
            insert.setString(2, UUID.randomUUID().toString());
            insert.setString(3, SalePayload.of(eventId, tickets).toJson());
            insert.setString(4, tickets.getFirst().soldAt().toString());
            insert.executeUpdate();
        }
    }

    /** This laptop's gate id, like "A". "" until one is set: codes then look like Day 102's. */
    public String gateId() {
        try (PreparedStatement select = connection.prepareStatement("select value from settings where name = 'gate_id'");
             ResultSet row = select.executeQuery()) {
            return row.next() ? row.getString(1) : "";
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the gate id", e);
        }
    }

    /** Day 117: one to three capital letters or digits, like A, B or G2. Short, because it's in every code. */
    public synchronized void setGateId(String gateId) {
        if (gateId == null || !gateId.matches("[A-Z0-9]{1,3}")) {
            throw new IllegalArgumentException("Give the gate a short id like A, B or G2");
        }
        try (PreparedStatement upsert = connection.prepareStatement(
                "insert into settings (name, value) values ('gate_id', ?)"
                        + " on conflict (name) do update set value = excluded.value")) {
            upsert.setString(1, gateId);
            upsert.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Couldn't save the gate id", e);
        }
    }

    /** Day 119: the token this laptop logs in to the sync server with, or "" before register. */
    public String syncToken() {
        try (PreparedStatement select = connection.prepareStatement("select value from settings where name = 'sync_token'");
             ResultSet row = select.executeQuery()) {
            return row.next() ? row.getString(1) : "";
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the sync token", e);
        }
    }

    public synchronized void setSyncToken(String token) {
        try (PreparedStatement upsert = connection.prepareStatement(
                "insert into settings (name, value) values ('sync_token', ?)"
                        + " on conflict (name) do update set value = excluded.value")) {
            upsert.setString(1, token);
            upsert.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Couldn't save the sync token", e);
        }
    }

    /** How many tickets of this type this gate issued itself: codes starting "A-REG-", or "REG-" with no id. */
    public int countIssuedHere(String eventId, String typeCode, String codePrefix) {
        try (PreparedStatement count = connection.prepareStatement(
                "select count(*) from tickets where event_id = ? and type_code = ? and code like ?")) {
            count.setString(1, eventId);
            count.setString(2, typeCode);
            count.setString(3, codePrefix + typeCode + "-%");
            try (ResultSet row = count.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't count " + typeCode + " tickets", e);
        }
    }

    /**
     * This gate's own sales after outbox id {@code afterId}, oldest first: the
     * feed another gate reads. Sales copied from other gates never go in the
     * outbox, so they're never passed on again.
     */
    public List<OutboxEntry> ownSalesAfter(long afterId, int limit) {
        try (PreparedStatement select = connection.prepareStatement(
                "select id, idempotency_key, payload, attempts from outbox where id > ? order by id limit ?")) {
            select.setLong(1, afterId);
            select.setInt(2, limit);
            List<OutboxEntry> entries = new ArrayList<>();
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    entries.add(new OutboxEntry(rows.getLong("id"), rows.getString("idempotency_key"),
                            rows.getString("payload"), rows.getInt("attempts")));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the outbox", e);
        }
    }

    /** The last outbox id read from this gate's feed, or 0 before the first pull. */
    public long peerCursor(String peer) {
        try (PreparedStatement select = connection.prepareStatement("select last_id from peer_cursors where peer = ?")) {
            select.setString(1, peer);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? row.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't read where we got to with gate " + peer, e);
        }
    }

    /**
     * Copies one sale from another gate's feed, and moves our place in that
     * feed past it, in one transaction.
     *
     * @return false when we already had it (a pull that was cut off and ran again)
     */
    public boolean applyPeerSale(String peer, OutboxEntry entry, Instant receivedAt) {
        SalePayload sale = SalePayload.fromJson(entry.payload());
        boolean[] isNew = {false};
        try {
            inTransaction(() -> {
                if (!alreadyCopied(entry.key())) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "insert into peer_sales (idempotency_key, peer, received_at) values (?, ?, ?)")) {
                        insert.setString(1, entry.key());
                        insert.setString(2, peer);
                        insert.setString(3, receivedAt.toString());
                        insert.executeUpdate();
                    }
                    for (SalePayload.TicketPayload ticket : sale.tickets()) {
                        insertCopiedTicket(sale.eventId(), ticket);
                    }
                    isNew[0] = true;
                }
                try (PreparedStatement cursor = connection.prepareStatement(
                        "insert into peer_cursors (peer, last_id) values (?, ?)"
                                + " on conflict (peer) do update set last_id = excluded.last_id")) {
                    cursor.setString(1, peer);
                    cursor.setLong(2, entry.id());
                    cursor.executeUpdate();
                }
            });
        } catch (SQLException e) {
            String first = sale.tickets().isEmpty() ? "?" : sale.tickets().getFirst().code();
            throw new StoreException("Couldn't copy " + first + " from gate " + peer
                    + ". Is it already here? Each gate needs its own gate id.", e);
        }
        return isNew[0];
    }

    private boolean alreadyCopied(String key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "select 1 from peer_sales where idempotency_key = ?")) {
            select.setString(1, key);
            try (ResultSet row = select.executeQuery()) {
                return row.next();
            }
        }
    }

    private void insertCopiedTicket(String eventId, SalePayload.TicketPayload ticket) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into tickets (event_id, code, type_code, buyer, buyer_phone, price_cents, sold_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, eventId);
            insert.setString(2, ticket.code());
            insert.setString(3, ticket.type());
            insert.setString(4, ticket.buyer());
            insert.setString(5, ticket.phone());
            insert.setLong(6, ticket.priceCents());
            insert.setString(7, ticket.soldAt());
            insert.executeUpdate();
        }
    }

    /** The oldest sales the server doesn't have yet, oldest first. */
    public List<OutboxEntry> waitingToSync(int limit) {
        try (PreparedStatement select = connection.prepareStatement(
                "select id, idempotency_key, payload, attempts from outbox"
                        + " where sent_at is null order by id limit ?")) {
            select.setInt(1, limit);
            List<OutboxEntry> entries = new ArrayList<>();
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    entries.add(new OutboxEntry(rows.getLong("id"), rows.getString("idempotency_key"),
                            rows.getString("payload"), rows.getInt("attempts")));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the outbox", e);
        }
    }

    public int countWaitingToSync() {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select count(*) from outbox where sent_at is null")) {
            row.next();
            return row.getInt(1);
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the outbox", e);
        }
    }

    public void markSynced(long outboxId, Instant at) {
        update("update outbox set sent_at = ?, sync_result = 'accepted' where id = ?", at.toString(), outboxId);
    }

    /** Day 120: the server has the sale and refused it. It stops waiting, and goes on the conflicts list. */
    public synchronized void markConflict(long outboxId, String detail, Instant at) {
        try (PreparedStatement update = connection.prepareStatement(
                "update outbox set sent_at = ?, sync_result = 'oversold', last_error = ? where id = ?")) {
            update.setString(1, at.toString());
            update.setString(2, detail);
            update.setLong(3, outboxId);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Couldn't update the outbox", e);
        }
    }

    /** Every sale the server refused because the places had gone, oldest first. */
    public List<SyncConflict> conflicts() {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select payload, last_error from outbox where sync_result = 'oversold' order by id")) {
            List<SyncConflict> conflicts = new ArrayList<>();
            while (rows.next()) {
                SalePayload sale = SalePayload.fromJson(rows.getString("payload"));
                SalePayload.TicketPayload first = sale.tickets().getFirst();
                conflicts.add(new SyncConflict(
                        sale.tickets().stream().map(SalePayload.TicketPayload::code).toList(),
                        first.buyer(), first.phone(), rows.getString("last_error")));
            }
            return conflicts;
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the outbox", e);
        }
    }

    public void markSyncFailed(long outboxId, String problem) {
        update("update outbox set attempts = attempts + 1, last_error = ? where id = ?", problem, outboxId);
    }

    /** The last reason a sale couldn't be sent, or "" if it never failed. */
    public String lastSyncError(long outboxId) {
        try (PreparedStatement select = connection.prepareStatement("select last_error from outbox where id = ?")) {
            select.setLong(1, outboxId);
            try (ResultSet row = select.executeQuery()) {
                return row.next() && row.getString(1) != null ? row.getString(1) : "";
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the outbox", e);
        }
    }

    private synchronized void update(String sql, String text, long id) {
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, text);
            update.setLong(2, id);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Couldn't update the outbox", e);
        }
    }

    /** One piece of database work that may throw SQLException. */
    @FunctionalInterface
    interface SqlWork {
        void run() throws SQLException;
    }

    /**
     * Runs the work as one transaction: commit if it all worked, roll back if
     * anything threw. Until commit, nothing is in gate.db for good; a crash
     * before then leaves gate.db as it was, and SQLite tidies up on the next open.
     */
    // synchronized (Day 118): the whole app shares this one connection. While one
    // thread is inside a transaction, another thread's statements would land in
    // that same transaction: committed by someone else's commit, or lost in
    // someone else's rollback. So only one thread at a time gets the connection.
    synchronized void inTransaction(SqlWork work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            work.run();
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** For the crash test only: runs after each ticket of a group sale is written. */
    void afterEachTicket(Runnable hook) {
        afterEachTicket = hook;
    }

    /** How many tickets the sales table says were sold, adding up every sale's quantity. */
    public int ticketsPromisedBySales(String eventId) {
        return countWith("select coalesce(sum(quantity), 0) from sales where event_id = ?", eventId);
    }

    /** How many tickets belong to a sale. After any crash, this equals ticketsPromisedBySales. */
    public int ticketsInSales(String eventId) {
        return countWith("select count(*) from tickets where event_id = ? and sale_id is not null", eventId);
    }

    /** SQLite's own check of the file: "ok" when nothing is damaged. */
    public String integrityCheck() {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("pragma integrity_check")) {
            row.next();
            return row.getString(1);
        } catch (SQLException e) {
            throw new StoreException("Couldn't check the database", e);
        }
    }

    /** "wal" once the store is open. */
    public String journalMode() {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("pragma journal_mode")) {
            row.next();
            return row.getString(1);
        } catch (SQLException e) {
            throw new StoreException("Couldn't read the journal mode", e);
        }
    }

    private int countWith(String sql, String eventId) {
        try (PreparedStatement count = connection.prepareStatement(sql)) {
            count.setString(1, eventId);
            try (ResultSet row = count.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        } catch (SQLException e) {
            throw new StoreException("Couldn't count for " + eventId, e);
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
    // synchronized for the same reason as inTransaction: never in the middle of someone else's sale.
    public synchronized boolean markCheckedIn(String eventId, String code, Instant at) {
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
