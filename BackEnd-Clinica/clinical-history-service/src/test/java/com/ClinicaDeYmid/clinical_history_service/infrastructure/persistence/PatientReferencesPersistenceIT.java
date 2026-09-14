package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcPatientReferences.class, ClockConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PatientReferencesPersistenceIT {

    @Autowired
    private JdbcPatientReferences references;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM patient_references");
    }

    @Test
    void storesAndReadsBothKindsOfPatients() {
        PatientReference.Registered registered = registered(UUID.randomUUID(), 0, PatientReference.Registered.Status.ACTIVE);
        PatientReference.Unidentified unidentified = unidentified(UUID.randomUUID(), 0, null);

        assertThat(references.saveIfNewer(registered)).isTrue();
        assertThat(references.saveIfNewer(unidentified)).isTrue();

        assertThat(references.find(registered.uuid())).contains(registered);
        assertThat(references.find(unidentified.uuid())).contains(unidentified);
    }

    @Test
    void appliesOnlyNewerVersions() {
        UUID uuid = UUID.randomUUID();
        references.saveIfNewer(registered(uuid, 2, PatientReference.Registered.Status.INACTIVE));

        assertThat(references.saveIfNewer(registered(uuid, 1, PatientReference.Registered.Status.ACTIVE))).isFalse();
        assertThat(references.saveIfNewer(registered(uuid, 2, PatientReference.Registered.Status.ACTIVE))).isFalse();
        assertThat(references.find(uuid)).hasValueSatisfying(reference ->
                assertThat(((PatientReference.Registered) reference).status()).isEqualTo(PatientReference.Registered.Status.INACTIVE));

        assertThat(references.saveIfNewer(registered(uuid, 3, PatientReference.Registered.Status.ACTIVE))).isTrue();
        assertThat(references.find(uuid)).hasValueSatisfying(reference -> assertThat(reference.version()).isEqualTo(3));
    }

    @Test
    void listsTheProvisionalRecordsLinkedToAPatientAndForgetsRevertedLinks() {
        UUID patient = UUID.randomUUID();
        UUID provisional = UUID.randomUUID();
        references.saveIfNewer(registered(patient, 0, PatientReference.Registered.Status.ACTIVE));
        references.saveIfNewer(unidentified(provisional, 1, patient));

        assertThat(references.subjectsOf(patient)).containsExactly(patient, provisional);

        references.saveIfNewer(unidentified(provisional, 2, null));

        assertThat(references.subjectsOf(patient)).containsExactly(patient);
    }

    @Test
    void databaseRejectsRowsThatMixBothKinds() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO patient_references (uuid, kind, source_version, code, estimated_birth_year, sex, status, updated_at)
                VALUES ('3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 'REGISTERED', 0, 'NN-2026-000001', 1980, 'MALE', 'ACTIVE', NOW(6))"""))
                .hasMessageContaining("chk_patient_references_kind");
    }

    static PatientReference.Registered registered(UUID uuid, long version, PatientReference.Registered.Status status) {
        return new PatientReference.Registered(uuid, version, new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE, status, null,
                "CONTRIBUTORY", "900123456-7");
    }

    static PatientReference.Unidentified unidentified(UUID uuid, long version, UUID identifiedAs) {
        return new PatientReference.Unidentified(uuid, version, "NN-2026-000042", PatientReference.Sex.MALE, 1980,
                identifiedAs == null ? PatientReference.Unidentified.Status.UNIDENTIFIED : PatientReference.Unidentified.Status.IDENTIFIED,
                identifiedAs, null);
    }
}
