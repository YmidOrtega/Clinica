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

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withEnv("PATIENT_DB_MIGRATOR_USER", MIGRATOR)
            .withEnv("PATIENT_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("PATIENT_DB_APP_USER", APP)
            .withEnv("PATIENT_DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh");

    private static JdbcTemplate app;

    @BeforeAll
    static void migrateWithTheMigratorUser() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/patient")
                .load()
                .migrate();
        app = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD));
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

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
