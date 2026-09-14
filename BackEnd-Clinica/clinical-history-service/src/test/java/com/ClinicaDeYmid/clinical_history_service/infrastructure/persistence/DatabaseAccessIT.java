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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseAccessIT {

    private static final String MIGRATOR = "clinical_migrator";
    private static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    private static final String APP = "clinical_app";
    private static final String APP_PASSWORD = "app-test-secret";

    private static final String PATIENT = "3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d";
    private static final String ENCOUNTER = "8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String NOTE = "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e";
    private static final String CLINICIAN = "00000000-0000-4000-8000-000000000003";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withEnv("CLINICAL_DB_MIGRATOR_USER", MIGRATOR)
            .withEnv("CLINICAL_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("CLINICAL_DB_APP_USER", APP)
            .withEnv("CLINICAL_DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh");

    private static JdbcTemplate app;
    private static JdbcTemplate migrator;
    private static TransactionTemplate appTransaction;

    @BeforeAll
    static void migrateAndRecordAClinicalNote() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/clinical").load().migrate();
        DriverManagerDataSource appDataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD);
        app = new JdbcTemplate(appDataSource);
        appTransaction = new TransactionTemplate(new DataSourceTransactionManager(appDataSource));
        migrator = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD));

        app.update("""
                INSERT INTO patient_references (uuid, kind, source_version, code, estimated_birth_year, sex, status, updated_at)
                VALUES (?, 'UNIDENTIFIED', 0, 'NN-2026-000001', 1980, 'MALE', 'UNIDENTIFIED', NOW(6))""", PATIENT);
        app.update("""
                INSERT INTO clinical_ledger.encounters (id, patient_uuid, type, opened_at, opened_by, opened_by_role)
                VALUES (?, ?, 'EMERGENCY', NOW(6), ?, 'DOCTOR')""", ENCOUNTER, PATIENT, CLINICIAN);
        app.update("""
                INSERT INTO clinical_ledger.notes
                    (id, encounter_id, type, content, author_uuid, author_role, occurred_at, recorded_at, extemporaneous)
                VALUES (?, ?, 'TRIAGE', '{"type": "TRIAGE", "level": "II", "reason": "Dolor torácico"}', ?, 'DOCTOR', NOW(6), NOW(6), FALSE)""",
                NOTE, ENCOUNTER, CLINICIAN);
    }

    @Test
    void applicationUserMaintainsTheLocalPatientCopy() {
        app.update("UPDATE patient_references SET source_version = 1 WHERE uuid = ?", PATIENT);

        assertThat(app.queryForObject("SELECT source_version FROM patient_references WHERE uuid = ?", Long.class, PATIENT)).isEqualTo(1);
    }

    @Test
    void applicationUserWritesAndDiscardsDrafts() {
        String draft = UUID.randomUUID().toString();
        app.update("""
                INSERT INTO clinical_workspace.note_drafts
                    (id, encounter_id, type, content, author_uuid, author_role, occurred_at, version, created_at, updated_at)
                VALUES (?, ?, 'NURSING', '{"type": "NURSING"}', ?, 'NURSE', NOW(6), 0, NOW(6), NOW(6))""", draft, ENCOUNTER, CLINICIAN);
        app.update("UPDATE clinical_workspace.note_drafts SET version = 1 WHERE id = ?", draft);
        app.update("DELETE FROM clinical_workspace.note_drafts WHERE id = ?", draft);

        assertThat(app.queryForObject("SELECT COUNT(*) FROM clinical_workspace.note_drafts WHERE id = ?", Long.class, draft)).isZero();
    }

    @Test
    void applicationUserLocksLedgerRowsToSerializeWrites() {
        String locked = appTransaction.execute(status -> app.queryForObject(
                "SELECT id FROM clinical_ledger.encounters WHERE id = ? FOR UPDATE", String.class, ENCOUNTER));

        assertThat(locked).isEqualTo(ENCOUNTER);
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

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE clinical_ledger.notes SET content = '{\"type\": \"TRIAGE\", \"level\": \"V\"}'",
            "DELETE FROM clinical_ledger.notes",
            "UPDATE clinical_ledger.encounters SET type = 'OUTPATIENT'",
            "DELETE FROM clinical_ledger.encounter_closures",
            "UPDATE clinical_ledger.note_voids SET reason = 'otra'",
            "DELETE FROM clinical_ledger.note_voids",
            "DROP TABLE clinical_ledger.notes",
            "ALTER TABLE clinical_ledger.notes DROP CHECK chk_notes_type",
            "CREATE TABLE clinical_ledger.shadow (id INT)",
            "CREATE TRIGGER clinical_ledger.tr_bypass BEFORE INSERT ON clinical_ledger.notes FOR EACH ROW SET NEW.extemporaneous = FALSE"
    })
    void applicationUserCannotRewriteTheClinicalRecord(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE clinical_ledger.notes SET extemporaneous = TRUE",
            "DELETE FROM clinical_ledger.notes"
    })
    void migrationsCannotRewriteSignedNotesEither(String statement) {
        assertDenied(() -> migrator.execute(statement));
    }

    private static void assertDenied(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .extracting(failure -> NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .asString()
                .containsAnyOf("command denied", "Access denied", "SUPER privilege");
    }
}
