package com.ClinicaDeYmid.clinical_history_service.domain.patient;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientReferenceTest {

    @Test
    void onlyActiveRegisteredPatientsAcceptNewEncounters() {
        assertThat(registered(PatientReference.Registered.Status.ACTIVE).acceptsNewEncounters()).isTrue();
        assertThat(registered(PatientReference.Registered.Status.INACTIVE).acceptsNewEncounters()).isFalse();
        assertThat(registered(PatientReference.Registered.Status.DECEASED).acceptsNewEncounters()).isFalse();
    }

    @Test
    void unidentifiedPatientsAcceptEncountersUntilIdentifiedOrDeceased() {
        UUID realPatient = UUID.randomUUID();

        assertThat(unidentified(PatientReference.Unidentified.Status.UNIDENTIFIED, null).acceptsNewEncounters()).isTrue();
        assertThat(unidentified(PatientReference.Unidentified.Status.IDENTIFIED, realPatient).acceptsNewEncounters()).isFalse();
        assertThat(unidentified(PatientReference.Unidentified.Status.IDENTIFIED, realPatient).identifiedAs()).contains(realPatient);
    }

    @Test
    void requiresTheLinkOnlyWhenIdentified() {
        assertThatThrownBy(() -> unidentified(PatientReference.Unidentified.Status.IDENTIFIED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unidentified(PatientReference.Unidentified.Status.UNIDENTIFIED, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static PatientReference.Registered registered(PatientReference.Registered.Status status) {
        return new PatientReference.Registered(UUID.randomUUID(), 0, new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"),
                "Ana", "Restrepo", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE, status,
                status == PatientReference.Registered.Status.DECEASED ? LocalDate.of(2026, 9, 1) : null, "UNINSURED", null);
    }

    static PatientReference.Unidentified unidentified(PatientReference.Unidentified.Status status, UUID identifiedAs) {
        return new PatientReference.Unidentified(UUID.randomUUID(), 0, "NN-2026-000001", PatientReference.Sex.MALE, 1980,
                status, identifiedAs, null);
    }
}
