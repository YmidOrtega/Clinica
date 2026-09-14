package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientRows;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "patient_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "patient_app";
    private static final String APP_PASSWORD = "app-test-secret";
    private static final String DEBEZIUM = "patient_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withEnv("PATIENT_DB_MIGRATOR_USER", MIGRATOR)
            .withEnv("PATIENT_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("PATIENT_DB_APP_USER", APP)
            .withEnv("PATIENT_DB_APP_PASSWORD", APP_PASSWORD)
            .withEnv("PATIENT_DB_DEBEZIUM_USER", DEBEZIUM)
            .withEnv("PATIENT_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh");

    private static JdbcTemplate app;
    private static JdbcTemplate debezium;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/patient")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
        String outboxUrl = MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(\\?|$)", "/patient_outbox$1");
        debezium = new JdbcTemplate(new DriverManagerDataSource(outboxUrl, DEBEZIUM, DEBEZIUM_PASSWORD));
    }

    @Test
    void applicationUserCanReadInsertAndUpdatePatients() {
        PatientRows.insert(app, PatientRows.valid());

        app.update("UPDATE patients SET mobile = '3119990000', version = version + 1");

        assertThat(app.queryForObject("SELECT mobile FROM patients", String.class)).isEqualTo("3119990000");
    }

    @Test
    void applicationUserCannotDeletePatients() {
        assertDenied(() -> app.update("DELETE FROM patients"));
        assertDenied(() -> app.update("DELETE FROM patients_aud"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE patients",
            "TRUNCATE TABLE patients_aud",
            "ALTER TABLE patients DROP CHECK chk_patients_status_consistency",
            "CREATE TABLE shadow (id INT)",
            "CREATE TRIGGER tr_bypass BEFORE INSERT ON patients FOR EACH ROW SET NEW.status = 'ACTIVE'",
            "CREATE INDEX idx_bypass ON patients (email)",
            "GRANT DELETE ON test.patients TO 'patient_app'@'%'"
    })
    void applicationUserCannotChangeTheSchemaOrItsPermissions(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void applicationUserCanWriteAndPurgeOnlyTheOutbox() {
        app.update("INSERT INTO patient_outbox.outbox_events (id, aggregatetype, aggregateid, type, payload, created_at) "
                + "VALUES ('6f0d2c1e-8b7a-4c3d-9e2f-1a0b9c8d7e6f', 'patient', '3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', "
                + "'PatientRegistered', '{}', NOW(6))");

        assertThat(app.update("DELETE FROM patient_outbox.outbox_events WHERE created_at < NOW(6) + INTERVAL 1 DAY")).isEqualTo(1);
        assertDenied(() -> app.update("UPDATE patient_outbox.outbox_events SET type = 'PatientDied'"));
        assertDenied(() -> app.execute("DROP TABLE patient_outbox.outbox_events"));
    }

    @Test
    void debeziumUserCanOnlyReadTheOutbox() {
        assertThat(debezium.queryForObject("SELECT COUNT(*) FROM patient_outbox.outbox_events", Integer.class)).isNotNull();
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM " + MYSQL.getDatabaseName() + ".patients", Integer.class));
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM " + MYSQL.getDatabaseName() + ".patients_aud", Integer.class));
        assertDenied(() -> debezium.update("INSERT INTO patient_outbox.outbox_events (id) VALUES ('x')"));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
