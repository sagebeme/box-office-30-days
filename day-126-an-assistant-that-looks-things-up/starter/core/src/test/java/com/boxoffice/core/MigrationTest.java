package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationTest {

    @Test
    void aGateDatabaseFromBeforeDay108OpensAndKeepsItsTickets(@TempDir Path folder) throws Exception {
        // Make a database the way the Day 107 app left it: only V1 has run.
        String url = "jdbc:sqlite:" + folder.resolve("gate.db");
        Flyway.configure().dataSource(url, null, null).target("1").load().migrate();
        try (Connection connection = DriverManager.getConnection(url);
             Statement sql = connection.createStatement()) {
            sql.execute("insert into events values ('old', 'Old Night', '2026-11-14T15:00:00Z', '2026-11-15T01:00:00Z')");
            sql.execute("insert into ticket_types values ('old', 'REG', 'Regular', 150000, 10, 0)");
            sql.execute("insert into tickets values ('old', 'REG-0001', 'REG', 'Otieno', 150000, '2026-11-14T18:00:00Z')");
        }

        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            Ticket old = store.tickets("old").getFirst();

            assertThat(old.buyer()).isEqualTo("Otieno");
            assertThat(old.phone()).isEmpty();
        }
    }
}
