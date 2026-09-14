package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.note.TriageLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SampleLedgerEntries {

    static final UUID PATIENT = UUID.fromString("3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d");
    static final UUID ENCOUNTER = UUID.fromString("8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    static final UUID NOTE = UUID.fromString("5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e");
    static final Clinician NURSE = new Clinician(UUID.fromString("00000000-0000-4000-8000-000000000004"), ClinicalRole.NURSE);
    static final Instant AT = Instant.parse("2026-09-10T14:00:00.123456Z");

    private SampleLedgerEntries() {
    }

    static List<ChainLink> seal(EcdsaClinicalSignature signature, List<LedgerEntry> entries) {
        List<ChainLink> links = new ArrayList<>();
        ChainLink previous = null;
        for (LedgerEntry entry : entries) {
            previous = signature.seal(entry, previous, AT);
            links.add(previous);
        }
        return links;
    }

    static List<LedgerEntry> everyKindOfEntry() {
        Encounter encounter = new Encounter(ENCOUNTER, PATIENT, EncounterType.EMERGENCY, null, AT, NURSE, new EncounterStatus.Open());
        return List.of(
                new LedgerEntry.EncounterOpened(encounter),
                triageNote("Dolor torácico"),
                new LedgerEntry.NoteVoided(PATIENT, new NoteVoid(NOTE, "Paciente equivocado", NURSE, AT.plusSeconds(600))),
                new LedgerEntry.EncounterClosed(PATIENT, new EncounterClosure(ENCOUNTER, AT.plusSeconds(3600), NURSE)));
    }

    static LedgerEntry triageNote(String reason) {
        return new LedgerEntry.NoteSigned(PATIENT, new SignedNote(NOTE, ENCOUNTER, NURSE, "nurse@clinica.test",
                new NoteContent.Triage(TriageLevel.II, reason, null), null, List.of(), List.of(), AT, AT.plusSeconds(300), false));
    }
}
