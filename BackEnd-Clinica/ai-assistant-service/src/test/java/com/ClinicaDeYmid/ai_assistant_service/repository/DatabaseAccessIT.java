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

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement).rootCause().hasMessageContaining("permission denied");
    }
}
