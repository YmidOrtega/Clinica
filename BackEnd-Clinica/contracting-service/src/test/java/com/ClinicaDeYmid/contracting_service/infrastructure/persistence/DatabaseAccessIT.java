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

    private static JdbcTemplate app;
    private static JdbcTemplate migrator;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/contracting")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
        migrator = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD));
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
