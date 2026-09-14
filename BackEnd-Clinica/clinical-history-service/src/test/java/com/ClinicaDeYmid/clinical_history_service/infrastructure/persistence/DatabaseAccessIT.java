package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
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

    private static final String MIGRATOR = "clinical_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "clinical_app";
    private static final String APP_PASSWORD = "app-test-secret";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withEnv("CLINICAL_DB_MIGRATOR_USER", MIGRATOR)
            .withEnv("CLINICAL_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("CLINICAL_DB_APP_USER", APP)
            .withEnv("CLINICAL_DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh");

    private static JdbcTemplate app;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/clinical").load().migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
    }

    @Test
    void applicationUserMaintainsTheLocalPatientCopy() {
        app.update("""
                INSERT INTO patient_references (uuid, kind, source_version, code, estimated_birth_year, sex, status, updated_at)
                VALUES ('3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 'UNIDENTIFIED', 0, 'NN-2026-000001', 1980, 'MALE', 'UNIDENTIFIED', NOW(6))""");
        app.update("UPDATE patient_references SET source_version = 1");

        assertThat(app.queryForObject("SELECT source_version FROM patient_references", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM patient_references",
            "DROP TABLE patient_references",
            "CREATE TABLE shadow (id INT)",
            "CREATE TRIGGER tr_bypass BEFORE INSERT ON patient_references FOR EACH ROW SET NEW.status = 'ACTIVE'"
    })
    void applicationUserCannotDeleteOrChangeTheSchema(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
