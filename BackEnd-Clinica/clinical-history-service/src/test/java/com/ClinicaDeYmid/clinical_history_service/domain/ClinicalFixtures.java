package com.ClinicaDeYmid.clinical_history_service.domain;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NotePolicy;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.note.TriageLevel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public final class ClinicalFixtures {

    public static final Instant OPENED_AT = Instant.parse("2026-09-10T14:00:00Z");
    public static final NotePolicy POLICY = new NotePolicy(Duration.ofHours(24), Duration.ofMinutes(2));

    private ClinicalFixtures() {
    }

    public static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("America/Bogota"));
    }

    public static Clinician doctor() {
        return new Clinician(UUID.randomUUID(), ClinicalRole.DOCTOR);
    }

    public static Clinician nurse() {
        return new Clinician(UUID.randomUUID(), ClinicalRole.NURSE);
    }

    public static Encounter openEncounter(EncounterType type) {
        return new Encounter(UUID.randomUUID(), UUID.randomUUID(), type, null, OPENED_AT, doctor(), new EncounterStatus.Open());
    }

    public static Encounter closed(Encounter encounter) {
        return new Encounter(encounter.id(), encounter.patientUuid(), encounter.type(), encounter.admissionId(), encounter.openedAt(),
                encounter.openedBy(), new EncounterStatus.Closed(OPENED_AT.plus(Duration.ofHours(3)), doctor()));
    }

    public static NoteContent.Progress progress() {
        return new NoteContent.Progress("Refiere menos dolor", "TA 120/80, abdomen blando", "Evolución favorable", "Continuar manejo");
    }

    public static NoteContent.Triage triage() {
        return new NoteContent.Triage(TriageLevel.III, "Dolor abdominal", null);
    }

    public static NoteContent.Nursing nursing() {
        return new NoteContent.Nursing("Paciente tranquilo", "Se administra analgésico según orden médica");
    }

    public static NoteContent.Discharge discharge() {
        return new NoteContent.Discharge("Ingresa por dolor abdominal", "Mejoría con analgesia", "Estable",
                "Dieta blanda", "Control en 8 días");
    }

    public static SignedNote signed(Encounter encounter, Clinician author, NoteContent content) {
        return new SignedNote(UUID.randomUUID(), encounter.id(), author, content, OPENED_AT.plusSeconds(600),
                OPENED_AT.plusSeconds(900), false);
    }
}
