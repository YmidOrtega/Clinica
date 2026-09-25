package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.support.SharedMySql;
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

import static com.ClinicaDeYmid.billing_service.support.SecretFiles.withSecretFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "billing_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "billing_app";
    private static final String APP_PASSWORD = "app-test-secret";
    private static final String DEBEZIUM = "billing_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";

    private static final String REVISION_ROW = "INSERT INTO billing_history.revisions (revised_at, revised_by) "
            + "VALUES (NOW(6), '3f2504e0-4f89-11d3-9a0c-0305e82c3301')";

    private static final String ISSUER_ROW = "INSERT INTO issuer (uuid, version, nit, verification_digit, person_type, "
            + "legal_name, tax_scheme, tax_responsibilities, address_line, municipality_code, city_name, department_name, "
            + "email, phone, health_provider_code, environment, created_at, updated_at) VALUES ('%s', 0, '800197268', 4, "
            + "'LEGAL_ENTITY', 'Clínica de Ymid S.A.S.', 'NOT_APPLICABLE', 'O-13', 'Calle 10 # 43-20', '05001', 'Medellín', "
            + "'Antioquia', 'facturacion@clinica.co', '6044441234', '050010123401', 'TEST', NOW(6), NOW(6))";

    @Container
    static final MySQLContainer<?> MYSQL = withSecretFiles(new MySQLContainer<>(SharedMySql.IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh"), Map.of(
            "BILLING_DB_MIGRATOR_USER", MIGRATOR,
            "BILLING_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD,
            "BILLING_DB_APP_USER", APP,
            "BILLING_DB_APP_PASSWORD", APP_PASSWORD,
            "BILLING_DB_DEBEZIUM_USER", DEBEZIUM,
            "BILLING_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD));

    private static JdbcTemplate app;
    private static JdbcTemplate debezium;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/billing")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
        String outboxUrl = MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(\\?|$)", "/billing_outbox$1");
        debezium = new JdbcTemplate(new DriverManagerDataSource(outboxUrl, DEBEZIUM, DEBEZIUM_PASSWORD));
    }

    @Test
    void theMigratorBuildsTheThreeSchemas() {
        assertThat(app.queryForList("SELECT schema_name FROM information_schema.schemata", String.class))
                .contains(MYSQL.getDatabaseName(), "billing_history", "billing_outbox");
    }

    @Test
    void applicationUserRecordsRevisionsButNeverRewritesThem() {
        assertThat(app.update(REVISION_ROW)).isEqualTo(1);

        assertDenied(() -> app.update("UPDATE billing_history.revisions SET revised_by = NULL"));
        assertDenied(() -> app.update("DELETE FROM billing_history.revisions"));
    }

    @Test
    void theDatabaseHoldsASingleIssuer() {
        app.update(ISSUER_ROW.formatted("3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d"));

        assertThatThrownBy(() -> app.update(ISSUER_ROW.formatted("6f0d2c1e-8b7a-4c3d-9e2f-1a0b9c8d7e6f")))
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .contains("uk_issuer_singleton");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM issuer",
            "DELETE FROM numbering_resolutions",
            "DELETE FROM numbering_counters"
    })
    void applicationUserNeverDeletesTheIssuerOrItsNumbering(String statement) {
        assertDenied(() -> app.update(statement));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE billing_history.revisions",
            "TRUNCATE TABLE billing_history.revisions",
            "CREATE TABLE shadow (id INT)",
            "CREATE TABLE billing_outbox.shadow (id INT)",
            "GRANT DELETE ON test.* TO 'billing_app'@'%'"
    })
    void applicationUserCannotChangeTheSchemaOrItsPermissions(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void debeziumUserSeesOnlyTheOutbox() {
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM billing_history.revisions", Integer.class));
        assertDenied(() -> debezium.execute("CREATE TABLE billing_outbox.shadow (id INT)"));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
