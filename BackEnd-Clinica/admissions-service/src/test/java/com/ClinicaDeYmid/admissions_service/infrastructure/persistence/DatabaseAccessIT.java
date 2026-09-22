package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.support.SharedPostgres;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.Map;
import java.util.UUID;

import static com.ClinicaDeYmid.admissions_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "admissions_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "admissions_app";
    private static final String APP_PASSWORD = "app-test-secret";
    private static final String DEBEZIUM = "admissions_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = withSecretFiles(new PostgreSQLContainer<>(SharedPostgres.IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/postgres-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "ADMISSIONS_DB_MIGRATOR_USER", MIGRATOR,
            "ADMISSIONS_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD,
            "ADMISSIONS_DB_APP_USER", APP,
            "ADMISSIONS_DB_APP_PASSWORD", APP_PASSWORD,
            "ADMISSIONS_DB_DEBEZIUM_USER", DEBEZIUM,
            "ADMISSIONS_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static JdbcTemplate app;
    private static JdbcTemplate debezium;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/admissions")
                .schemas("admissions")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), APP, APP_PASSWORD));
        debezium = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), DEBEZIUM, DEBEZIUM_PASSWORD));
    }

    @Test
    void theMigratorRunsEveryMigrationWithoutBeingASuperuser() {
        JdbcTemplate migrator = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD));

        assertThat(migrator.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = ?", Boolean.class, MIGRATOR))
                .isFalse();
        assertThat(migrator.queryForObject("SELECT count(*) FROM admissions.flyway_schema_history WHERE success",
                Integer.class)).isGreaterThan(10);
        assertThat(migrator.queryForObject("SELECT count(*) FROM pg_publication WHERE pubname = 'admissions_outbox_pub'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void theApplicationUserReadsInsertsAndUpdatesButNeverDeletes() {
        UUID location = UUID.randomUUID();
        app.update("INSERT INTO admissions.locations (uuid, version, name, status, created_at, updated_at) "
                + "VALUES (?, 0, 'Piso de prueba', 'ACTIVE', now(), now())", location);

        app.update("UPDATE admissions.locations SET name = 'Piso corregido' WHERE uuid = ?", location);

        assertThat(app.queryForObject("SELECT name FROM admissions.locations WHERE uuid = ?", String.class, location))
                .isEqualTo("Piso corregido");
        assertDenied(() -> app.update("DELETE FROM admissions.locations"));
        assertDenied(() -> app.update("DELETE FROM admissions_history.revisions"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE admissions.admissions",
            "TRUNCATE TABLE admissions.locations",
            "ALTER TABLE admissions.admissions DROP CONSTRAINT chk_admissions_status",
            "CREATE TABLE admissions.shadow (id INT)",
            "CREATE INDEX idx_bypass ON admissions.locations (name)"
    })
    void theApplicationUserCannotChangeTheSchemaOrItsPermissions(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void theApplicationUserCannotGrantItselfMorePrivileges() {
        app.execute("GRANT DELETE ON admissions.locations TO admissions_app");

        assertDenied(() -> app.update("DELETE FROM admissions.locations"));
    }

    @Test
    void theApplicationUserWritesAndPurgesOnlyTheOutbox() {
        app.update("INSERT INTO admissions_outbox.outbox_events (id, aggregatetype, aggregateid, type, payload, "
                        + "created_at) VALUES (?, 'admissions.events', ?, 'AdmissionRegistered', '{}'::jsonb, now())",
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(app.update("DELETE FROM admissions_outbox.outbox_events")).isEqualTo(1);
        assertDenied(() -> app.update("UPDATE admissions_outbox.outbox_events SET type = 'AdmissionDischarged'"));
        assertDenied(() -> app.execute("DROP TABLE admissions_outbox.outbox_events"));
    }

    @Test
    void theDebeziumUserOnlyReadsTheOutboxAndMayReplicate() {
        assertThat(debezium.queryForObject("SELECT count(*) FROM admissions_outbox.outbox_events", Integer.class))
                .isNotNull();
        assertThat(debezium.queryForObject("SELECT rolreplication FROM pg_roles WHERE rolname = ?", Boolean.class,
                DEBEZIUM)).isTrue();

        assertDenied(() -> debezium.queryForObject("SELECT count(*) FROM admissions.admissions", Integer.class));
        assertDenied(() -> debezium.queryForObject("SELECT count(*) FROM admissions_history.admissions_aud",
                Integer.class));
        assertDenied(() -> debezium.update("INSERT INTO admissions_outbox.outbox_events (id) VALUES (?)",
                UUID.randomUUID()));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("permission denied", "must be owner", "permiso denegado");
    }
}
