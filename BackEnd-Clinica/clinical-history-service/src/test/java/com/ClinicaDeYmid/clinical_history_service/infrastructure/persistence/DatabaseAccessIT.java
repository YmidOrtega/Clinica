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
    private static final String DEBEZIUM = "clinical_debezium";
    private static final String DEBEZIUM_PASSWORD = "debezium-test-secret";

    private static final String PATIENT = "3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d";
    private static final String ENCOUNTER = "8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String NOTE = "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e";
    private static final String CLINICIAN = "00000000-0000-4000-8000-000000000003";
    private static final String DATA_KEY = "7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestContainer.IMAGE)
            .withEnv("CLINICAL_DB_MIGRATOR_USER", MIGRATOR)
            .withEnv("CLINICAL_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("CLINICAL_DB_APP_USER", APP)
            .withEnv("CLINICAL_DB_APP_PASSWORD", APP_PASSWORD)
            .withEnv("CLINICAL_DB_DEBEZIUM_USER", DEBEZIUM)
            .withEnv("CLINICAL_DB_DEBEZIUM_PASSWORD", DEBEZIUM_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-create-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-users.sh");

    private static JdbcTemplate app;
    private static JdbcTemplate migrator;
    private static JdbcTemplate debezium;
    private static TransactionTemplate appTransaction;

    @BeforeAll
    static void migrateAndRecordAClinicalNote() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration/clinical").load().migrate();
        DriverManagerDataSource appDataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), APP, APP_PASSWORD);
        app = new JdbcTemplate(appDataSource);
        appTransaction = new TransactionTemplate(new DataSourceTransactionManager(appDataSource));
        migrator = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD));
        debezium = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/clinical_outbox"),
                DEBEZIUM, DEBEZIUM_PASSWORD));

        app.update("""
                INSERT INTO patient_references (uuid, kind, source_version, code, estimated_birth_year, sex, status, updated_at)
                VALUES (?, 'UNIDENTIFIED', 0, 'NN-2026-000001', 1980, 'MALE', 'UNIDENTIFIED', NOW(6))""", PATIENT);
        app.update("""
                INSERT INTO clinical_ledger.encounters (id, patient_uuid, type, opened_at, opened_by, opened_by_role)
                VALUES (?, ?, 'EMERGENCY', NOW(6), ?, 'DOCTOR')""", ENCOUNTER, PATIENT, CLINICIAN);
        app.update("INSERT INTO clinical_keys.data_keys (id, patient_uuid, created_at) VALUES (?, ?, NOW(6))", DATA_KEY, PATIENT);
        app.update("""
                INSERT INTO clinical_keys.data_key_wrappings (data_key_id, master_key_id, wrapped_key, created_at)
                VALUES (?, 'master-2026', RANDOM_BYTES(61), NOW(6))""", DATA_KEY);
        app.update("""
                INSERT INTO clinical_ledger.notes
                    (id, encounter_id, type, content_key_id, content_ciphertext, author_uuid, author_role, author_email, occurred_at,
                     recorded_at, extemporaneous)
                VALUES (?, ?, 'TRIAGE', ?, RANDOM_BYTES(80), ?, 'DOCTOR', 'doctor@clinica.test', NOW(6), NOW(6), FALSE)""",
                NOTE, ENCOUNTER, DATA_KEY, CLINICIAN);
        app.update("""
                INSERT INTO clinical_ledger.care_team_members (encounter_id, clinician_uuid, clinician_role, added_by, added_at)
                VALUES (?, ?, 'DOCTOR', ?, NOW(6))""", ENCOUNTER, CLINICIAN, CLINICIAN);
        app.update("""
                INSERT INTO clinical_ledger.emergency_accesses (id, patient_uuid, clinician_uuid, clinician_role, reason_key_id,
                    reason_ciphertext, granted_at, expires_at)
                VALUES (UUID(), ?, ?, 'NURSE', ?, RANDOM_BYTES(60), NOW(6), NOW(6) + INTERVAL 4 HOUR)""", PATIENT, CLINICIAN, DATA_KEY);
        app.update("""
                INSERT INTO clinical_ledger.chain_links (patient_uuid, sequence, entry_type, entry_id, format_version, payload_hash,
                    previous_hash, entry_hash, key_id, seal, sealed_at)
                VALUES (?, 1, 'NOTE_SIGNED', ?, 1, REPEAT('a', 64), REPEAT('0', 64), REPEAT('b', 64), 'k1', 'c2VhbA==', NOW(6))""",
                PATIENT, NOTE);
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
                    (id, encounter_id, type, content_key_id, content_ciphertext, author_uuid, author_role, occurred_at, version, created_at,
                     updated_at)
                VALUES (?, ?, 'NURSING', ?, RANDOM_BYTES(40), ?, 'NURSE', NOW(6), 0, NOW(6), NOW(6))""", draft, ENCOUNTER, DATA_KEY, CLINICIAN);
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
            "UPDATE clinical_ledger.notes SET content_ciphertext = RANDOM_BYTES(80)",
            "DELETE FROM clinical_ledger.notes",
            "UPDATE clinical_ledger.encounters SET type = 'OUTPATIENT'",
            "DELETE FROM clinical_ledger.encounter_closures",
            "UPDATE clinical_ledger.note_voids SET reason_ciphertext = RANDOM_BYTES(40)",
            "DELETE FROM clinical_ledger.note_voids",
            "DROP TABLE clinical_ledger.notes",
            "ALTER TABLE clinical_ledger.notes DROP CHECK chk_notes_type",
            "UPDATE clinical_ledger.chain_links SET seal = 'forged'",
            "DELETE FROM clinical_ledger.chain_links",
            "DELETE FROM clinical_ledger.care_team_members",
            "UPDATE clinical_ledger.care_team_members SET clinician_uuid = UUID()",
            "UPDATE clinical_ledger.emergency_accesses SET expires_at = NOW(6) + INTERVAL 1 YEAR",
            "DELETE FROM clinical_ledger.emergency_accesses",
            "CREATE TABLE clinical_ledger.shadow (id INT)",
            "CREATE TRIGGER clinical_ledger.tr_bypass BEFORE INSERT ON clinical_ledger.notes FOR EACH ROW SET NEW.extemporaneous = FALSE"
    })
    void applicationUserCannotRewriteTheClinicalRecord(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE clinical_keys.data_key_wrappings SET wrapped_key = RANDOM_BYTES(61)",
            "DELETE FROM clinical_keys.data_key_wrappings",
            "UPDATE clinical_keys.data_keys SET patient_uuid = UUID()",
            "DELETE FROM clinical_keys.data_keys",
            "DROP TABLE clinical_keys.data_keys"
    })
    void applicationUserCannotReplaceOrDestroyDataKeys(String statement) {
        assertDenied(() -> app.execute(statement));
    }

    @Test
    void applicationUserPublishesAndPurgesAuditEventsButCannotAlterThem() {
        String event = UUID.randomUUID().toString();
        app.update("""
                INSERT INTO clinical_outbox.outbox_events (id, aggregatetype, aggregateid, type, payload, created_at)
                VALUES (?, 'clinical.access-audit', ?, 'ClinicalRecordAccessed', '{}', NOW(6))""", event, PATIENT);

        assertDenied(() -> app.update("UPDATE clinical_outbox.outbox_events SET payload = '{\"outcome\": \"GRANTED\"}'"));
        assertThat(debezium.queryForObject("SELECT COUNT(*) FROM clinical_outbox.outbox_events WHERE id = ?", Long.class, event)).isEqualTo(1);
        assertDenied(() -> debezium.queryForObject("SELECT COUNT(*) FROM clinical_ledger.notes", Long.class));
        assertDenied(() -> debezium.update("DELETE FROM clinical_outbox.outbox_events"));
        assertThat(app.update("DELETE FROM clinical_outbox.outbox_events WHERE id = ?", event)).isEqualTo(1);
    }

    @Test
    void applicationUserAddsWrappingsWhenMasterKeysRotate() {
        app.update("""
                INSERT INTO clinical_keys.data_key_wrappings (data_key_id, master_key_id, wrapped_key, created_at)
                VALUES (?, 'master-2027', RANDOM_BYTES(61), NOW(6))""", DATA_KEY);

        assertThat(app.queryForObject("SELECT COUNT(*) FROM clinical_keys.data_key_wrappings WHERE data_key_id = ?", Long.class, DATA_KEY))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE clinical_ledger.notes SET extemporaneous = TRUE",
            "DELETE FROM clinical_ledger.notes",
            "UPDATE clinical_ledger.chain_links SET seal = 'forged'"
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
