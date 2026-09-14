package com.ClinicaDeYmid.clinical_history_service.infrastructure.events;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

record EncounterEventMessage(
        UUID eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        UUID patientUuid,
        UUID encounterId,
        Map<String, Object> data,
        Chain chain) {

    static final int SCHEMA_VERSION = 1;

    record Chain(long sequence, String entryHash, String previousHash, String keyId) {
    }

    record Person(UUID uuid, String role) {
        static Person of(Clinician clinician) {
            return new Person(clinician.uuid(), clinician.role().name());
        }
    }

    record CodedDiagnosis(String code, String display, String catalogVersion, String role, String type) {
        static CodedDiagnosis of(Diagnosis diagnosis) {
            return new CodedDiagnosis(diagnosis.code(), diagnosis.display(), diagnosis.catalogVersion(), diagnosis.role().name(),
                    diagnosis.type().name());
        }
    }

    static EncounterEventMessage of(LedgerEntry entry, ChainLink link, UUID eventId, String traceId) {
        Map<String, Object> data = new TreeMap<>();
        String type;
        UUID encounterId;
        Instant occurredAt;
        switch (entry) {
            case LedgerEntry.EncounterOpened opened -> {
                type = "EncounterOpened";
                encounterId = opened.encounter().id();
                occurredAt = opened.encounter().openedAt();
                data.put("encounterType", opened.encounter().type().name());
                data.put("admissionId", opened.encounter().admissionId());
                data.put("openedBy", Person.of(opened.encounter().openedBy()));
            }
            case LedgerEntry.NoteSigned signed -> {
                SignedNote note = signed.note();
                type = "ClinicalNoteSigned";
                encounterId = note.encounterId();
                occurredAt = note.recordedAt();
                data.put("noteId", note.id());
                data.put("noteType", note.type().name());
                data.put("restricted", note.isRestricted());
                data.put("author", Person.of(note.author()));
                data.put("careOccurredAt", note.occurredAt());
                data.put("extemporaneous", note.extemporaneous());
                data.put("diagnoses", note.content().diagnoses().stream().map(CodedDiagnosis::of).toList());
                data.put("amendsNoteId", note.content() instanceof NoteContent.Addendum addendum
                        ? addendum.amendsNoteId() : null);
                data.put("listChanges", note.updates().stream().filter(update -> !(update instanceof AppliedUpdate.VitalSignObserved)).count());
                data.put("vitalSigns", note.updates().stream().filter(AppliedUpdate.VitalSignObserved.class::isInstance).count());
                data.put("attachments", note.attachments().size());
            }
            case LedgerEntry.NoteVoided voided -> {
                type = "ClinicalNoteVoided";
                encounterId = null;
                occurredAt = voided.noteVoid().voidedAt();
                data.put("noteId", voided.noteVoid().noteId());
                data.put("voidedBy", Person.of(voided.noteVoid().voidedBy()));
            }
            case LedgerEntry.EncounterClosed closed -> {
                type = "EncounterClosed";
                encounterId = closed.closure().encounterId();
                occurredAt = closed.closure().closedAt();
                data.put("closedBy", Person.of(closed.closure().closedBy()));
            }
            case LedgerEntry.Unreadable unreadable -> throw new IllegalArgumentException("Unreadable entries are never published");
        }
        return new EncounterEventMessage(eventId, type, SCHEMA_VERSION, occurredAt, traceId, entry.patientUuid(), encounterId, data,
                new Chain(link.sequence(), link.entryHash(), link.previousHash(), link.keyId()));
    }
}
