package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
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

import java.util.Map;

import static com.ClinicaDeYmid.contracting_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "contracting_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "contracting_app";
    private static final String APP_PASSWORD = "app-test-secret";
    private static final String DEBEZIUM = "contracting_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";

    private static final String PAYER_ROW = "INSERT INTO payers (uuid, version, social_reason, nit, nit_verification_digit, type, "
            + "address, phone, status, created_at, updated_at) VALUES "
            + "('3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 0, 'Salud Total EPS S.A.', '901234567', 7, 'EPS', "
            + "'Calle 100 # 7-33', '6017429000', 'ACTIVE', NOW(6), NOW(6))";

    @Container
    static final MySQLContainer<?> MYSQL = withSecretFiles(new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "CONTRACTING_DB_MIGRATOR_USER", MIGRATOR,
            "CONTRACTING_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD,
            "CONTRACTING_DB_APP_USER", APP,
            "CONTRACTING_DB_APP_PASSWORD", APP_PASSWORD,
            "CONTRACTING_DB_DEBEZIUM_USER", DEBEZIUM,
            "CONTRACTING_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static final String OUTBOX_ROW = "INSERT INTO contracting_outbox.outbox_events "
            + "(id, aggregatetype, aggregateid, type, payload, created_at) VALUES "
            + "('6f0d2c1e-8b7a-4c3d-9e2f-1a0b9c8d7e6f', 'contracting.contracts', "
            + "'3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 'ContractDrafted', '{}', NOW(6))";

    private static JdbcTemplate app;
    private static JdbcTemplate migrator;
    private static JdbcTemplate debezium;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/contracting")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
        migrator = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD));
        String outboxUrl = MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(\\?|$)", "/contracting_outbox$1");
        debezium = new JdbcTemplate(new DriverManagerDataSource(outboxUrl, DEBEZIUM, DEBEZIUM_PASSWORD));
    }

    @Test
    void applicationUserCanReadInsertAndUpdatePayers() {
        migrator.update("DELETE FROM payers");
        app.update(PAYER_ROW);

        app.update("UPDATE payers SET phone = '6019990000', version = version + 1");

        assertThat(app.queryForObject("SELECT phone FROM payers", String.class)).isEqualTo("6019990000");
    }

    @Test
    void applicationUserCannotDeleteAnything() {
        assertDenied(() -> app.update("DELETE FROM payers"));
        assertDenied(() -> app.update("DELETE FROM contracting_history.payers_aud"));
        assertDenied(() -> app.update("DELETE FROM contracting_history.revisions"));
    }

    @Test
    void applicationUserCannotRewriteTheHistory() {
        assertDenied(() -> app.update("UPDATE contracting_history.payers_aud SET social_reason = 'Otra'"));
        assertDenied(() -> app.update("UPDATE contracting_history.revisions SET revised_by = NULL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE payers",
            "TRUNCATE TABLE contracting_history.payers_aud",
            "ALTER TABLE payers DROP CHECK chk_payers_status_details",
            "CREATE TABLE shadow (id INT)",
            "CREATE TRIGGER tr_bypass BEFORE INSERT ON payers FOR EACH ROW SET NEW.status = 'ACTIVE'",
            "CREATE INDEX idx_bypass ON payers (phone)",
            "GRANT DELETE ON test.payers TO 'contracting_app'@'%'"
    })
    void applicationUserCannotChangeTheSchemaOrItsPermissions(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void theDatabaseRefusesRowsThatBreakTheDomainRules() {
        migrator.update("DELETE FROM payers");

        assertRejected(() -> app.update(PAYER_ROW.replace("'ACTIVE'", "'RETIRADO'")));
        assertRejected(() -> app.update(PAYER_ROW.replace("'EPS'", "'COOPERATIVA'")));
        assertRejected(() -> app.update(PAYER_ROW.replace("'901234567'", "'90A234567'")));
        assertRejected(() -> app.update(PAYER_ROW.replace("'6017429000'", "'123'")));
        assertRejected(() -> app.update(PAYER_ROW.replace("'ACTIVE', NOW(6)", "'SUSPENDED', NOW(6)")));
    }

    @Test
    void applicationUserCanWriteAndPurgeOnlyTheOutbox() {
        migrator.update("DELETE FROM contracting_outbox.outbox_events");
        app.update(OUTBOX_ROW);

        assertThat(app.update("DELETE FROM contracting_outbox.outbox_events WHERE created_at < NOW(6) + INTERVAL 1 DAY"))
                .isEqualTo(1);
        assertDenied(() -> app.update("UPDATE contracting_outbox.outbox_events SET type = 'ContractActivated'"));
        assertDenied(() -> app.execute("DROP TABLE contracting_outbox.outbox_events"));
    }

    @Test
    void theOutboxRefusesEventsThatAreNotPartOfTheContract() {
        migrator.update("DELETE FROM contracting_outbox.outbox_events");

        assertRejected(() -> app.update(OUTBOX_ROW.replace("'ContractDrafted'", "'ContractInvented'")));
        assertRejected(() -> app.update(OUTBOX_ROW.replace("'contracting.contracts'", "'contracting.secrets'")));
    }

    @Test
    void debeziumUserCanOnlyReadTheOutbox() {
        assertThat(debezium.queryForObject("SELECT COUNT(*) FROM contracting_outbox.outbox_events", Integer.class)).isNotNull();
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM " + MYSQL.getDatabaseName() + ".contracts", Integer.class));
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM contracting_history.payers_aud", Integer.class));
        assertDenied(() -> debezium.update("DELETE FROM contracting_outbox.outbox_events"));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }

    private static void assertRejected(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("Check constraint", "CONSTRAINT", "Data truncated", "Incorrect");
    }
}
