package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.withTheServiceUsers();

    private static JdbcTemplate app;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(PostgresTestContainer.urlOf(POSTGRES), PostgresTestContainer.MIGRATOR,
                        PostgresTestContainer.MIGRATOR_PASSWORD)
                .schemas("assistant_migrations", "assistant")
                .defaultSchema("assistant_migrations")
                .createSchemas(false)
                .locations("classpath:db/migration/assistant")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(PostgresTestContainer.urlOf(POSTGRES),
                PostgresTestContainer.APP, PostgresTestContainer.APP_PASSWORD));
    }

    @Test
    void theApplicationReadsWritesAndUpdatesConversations() {
        UUID uuid = UUID.randomUUID();
        app.update("INSERT INTO assistant.conversations (uuid, version, owner_uuid, title, status, created_at, updated_at) "
                + "VALUES (?, 0, ?, 'Revisión', 'OPEN', now(), now())", uuid, UUID.randomUUID());

        app.update("UPDATE assistant.conversations SET title = 'Revisión diaria' WHERE uuid = ?", uuid);

        assertThat(app.queryForObject("SELECT title FROM assistant.conversations WHERE uuid = ?", String.class, uuid))
                .isEqualTo("Revisión diaria");
    }

    @Test
    void theApplicationNeitherDeletesNorChangesTheSchemaNorTouchesTheMigrationHistory() {
        assertDenied(() -> app.update("DELETE FROM assistant.conversations"));
        assertDenied(() -> app.execute("CREATE TABLE assistant.intruders (id INT)"));
        assertDenied(() -> app.execute("CREATE TABLE public.intruders (id INT)"));
        assertDenied(() -> app.queryForList("SELECT * FROM assistant_migrations.flyway_schema_history"));
    }

    @Test
    void theDatabaseRefusesAClosedConversationWithoutItsClosingTime() {
        assertThatThrownBy(() -> app.update("INSERT INTO assistant.conversations "
                + "(uuid, version, owner_uuid, title, status, created_at, updated_at) "
                + "VALUES (?, 0, ?, 'Revisión', 'CLOSED', now(), now())", UUID.randomUUID(), UUID.randomUUID()))
                .rootCause().hasMessageContaining("chk_conversations_closed");
    }

    @Test
    void thePurgeForgetsOldConversationsButKeepsTheActionsAsAudit() {
        UUID owner = UUID.randomUUID();
        UUID old = conversation(owner, "now() - interval '31 days'");
        UUID recent = conversation(owner, "now()");
        app.update("INSERT INTO assistant.conversation_messages (uuid, conversation_id, position, role, content, created_at) "
                + "SELECT ?, id, 1, 'USER', '¿Qué pasó con la SETP1?', now() FROM assistant.conversations WHERE uuid = ?",
                UUID.randomUUID(), old);
        UUID action = UUID.randomUUID();
        app.update("INSERT INTO assistant.proposed_actions (uuid, version, conversation_id, owner_uuid, invoice_uuid, "
                + "invoice_number, kind, reason, status, proposed_at, expires_at, decided_at, outcome) "
                + "SELECT ?, 2, id, ?, ?, 'SETP1', 'SEND_TO_DIAN', 'Reenviar', 'DONE', now(), now() + interval '30 minutes', "
                + "now(), 'Enviada' FROM assistant.conversations WHERE uuid = ?", action, owner, UUID.randomUUID(), old);

        Integer purged = app.queryForObject("SELECT assistant.purge_conversations(now() - interval '30 days')", Integer.class);

        assertThat(purged).isEqualTo(1);
        assertThat(app.queryForObject("SELECT count(*) FROM assistant.conversations WHERE uuid IN (?, ?)", Integer.class,
                old, recent)).isEqualTo(1);
        assertThat(app.queryForObject("SELECT conversation_id IS NULL AND status = 'DONE' FROM assistant.proposed_actions "
                + "WHERE uuid = ?", Boolean.class, action)).isTrue();
        assertDenied(() -> app.update("DELETE FROM assistant.conversation_messages"));
        assertDenied(() -> app.update("DELETE FROM assistant.proposed_actions"));
    }

    private static UUID conversation(UUID owner, String touched) {
        UUID uuid = UUID.randomUUID();
        app.update("INSERT INTO assistant.conversations (uuid, version, owner_uuid, title, status, created_at, updated_at) "
                + "VALUES (?, 0, ?, 'Revisión', 'OPEN', " + touched + ", " + touched + ")", uuid, owner);
        return uuid;
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement).rootCause().hasMessageContaining("permission denied");
    }
}
