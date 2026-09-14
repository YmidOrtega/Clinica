package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.OPENED_AT;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.clockAt;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.closed;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.discharge;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nursing;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.openEncounter;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.progress;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.signed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterTest {

    private final Clock clock = clockAt(OPENED_AT.plusSeconds(7200));

    @Test
    void opensForPatientsThatAcceptEncounters() {
        Clinician nurse = nurse();
        PatientReference patient = unidentified(PatientReference.Unidentified.Status.UNIDENTIFIED);

        Encounter encounter = Encounter.open(patient, EncounterType.EMERGENCY, "  ADM-2026-0001 ", nurse, clock);

        assertThat(encounter.patientUuid()).isEqualTo(patient.uuid());
        assertThat(encounter.admissionId()).isEqualTo("ADM-2026-0001");
        assertThat(encounter.openedAt()).isEqualTo(clock.instant());
        assertThat(encounter.isOpen()).isTrue();
    }

    @Test
    void rejectsPatientsThatNoLongerAcceptEncounters() {
        assertThatThrownBy(() -> Encounter.open(unidentified(PatientReference.Unidentified.Status.DECEASED), EncounterType.EMERGENCY,
                null, doctor(), clock)).isInstanceOf(ClinicalException.PatientNotAcceptingEncounters.class);
    }

    @ParameterizedTest
    @EnumSource(value = EncounterType.class, names = {"EMERGENCY", "INPATIENT"})
    void hospitalCareClosesOnlyWithAValidDischargeSummary(EncounterType type) {
        Encounter encounter = openEncounter(type);
        SignedNote evolution = signed(encounter, doctor(), progress());
        SignedNote summary = signed(encounter, doctor(), discharge());

        assertThatThrownBy(() -> encounter.close(doctor(), List.of(evolution), Set.of(), clock))
                .isInstanceOf(ClinicalException.EncounterNotReadyToClose.class)
                .hasMessageContaining("epicrisis");
        assertThatThrownBy(() -> encounter.close(doctor(), List.of(evolution, summary), Set.of(summary.id()), clock))
                .isInstanceOf(ClinicalException.EncounterNotReadyToClose.class);

        EncounterClosure closure = encounter.close(doctor(), List.of(evolution, summary), Set.of(), clock);

        assertThat(closure.encounterId()).isEqualTo(encounter.id());
        assertThat(closure.closedAt()).isEqualTo(clock.instant());
    }

    @ParameterizedTest
    @EnumSource(value = EncounterType.class, names = {"OUTPATIENT", "TELEHEALTH"})
    void ambulatoryCareClosesWithAnySignedNoteThatIsNotAnAddendum(EncounterType type) {
        Encounter encounter = openEncounter(type);
        SignedNote note = signed(encounter, doctor(), nursing());
        SignedNote addendum = signed(encounter, doctor(), new NoteContent.Addendum(note.id(), "Corrige la hora"));

        assertThatThrownBy(() -> encounter.close(doctor(), List.of(), Set.of(), clock))
                .isInstanceOf(ClinicalException.EncounterNotReadyToClose.class);
        assertThatThrownBy(() -> encounter.close(doctor(), List.of(note, addendum), Set.of(note.id()), clock))
                .isInstanceOf(ClinicalException.EncounterNotReadyToClose.class);
        assertThat(encounter.close(doctor(), List.of(note, addendum), Set.of(), clock)).isNotNull();
    }

    @Test
    void ignoresNotesFromOtherEncounters() {
        Encounter encounter = openEncounter(EncounterType.OUTPATIENT);
        SignedNote foreign = signed(openEncounter(EncounterType.OUTPATIENT), doctor(), progress());

        assertThatThrownBy(() -> encounter.close(doctor(), List.of(foreign), Set.of(), clock))
                .isInstanceOf(ClinicalException.EncounterNotReadyToClose.class);
    }

    @Test
    void cannotBeClosedTwice() {
        Encounter encounter = closed(openEncounter(EncounterType.OUTPATIENT));

        assertThatThrownBy(() -> encounter.close(doctor(), List.of(signed(encounter, doctor(), progress())), Set.of(), clock))
                .isInstanceOf(ClinicalException.EncounterClosed.class);
    }

    private static PatientReference.Unidentified unidentified(PatientReference.Unidentified.Status status) {
        return new PatientReference.Unidentified(UUID.randomUUID(), 0, "NN-2026-000001", PatientReference.Sex.MALE, 1980, status, null,
                status == PatientReference.Unidentified.Status.DECEASED ? LocalDate.of(2026, 9, 10) : null);
    }
}
