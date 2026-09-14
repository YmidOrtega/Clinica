package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Encounter(
        UUID id,
        UUID patientUuid,
        EncounterType type,
        String admissionId,
        Instant openedAt,
        Clinician openedBy,
        EncounterStatus status) {

    public Encounter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(status, "status");
    }

    public static Encounter open(PatientReference patient, EncounterType type, String admissionId, Clinician openedBy, Clock clock) {
        ClinicalText.present(type, "type");
        if (!patient.acceptsNewEncounters()) {
            throw new ClinicalException.PatientNotAcceptingEncounters();
        }
        return new Encounter(UUID.randomUUID(), patient.uuid(), type, ClinicalText.optional(admissionId, "admissionId", 64),
                Instant.now(clock), openedBy, new EncounterStatus.Open());
    }

    public boolean isOpen() {
        return status instanceof EncounterStatus.Open;
    }

    public void requireOpen() {
        if (!isOpen()) {
            throw new ClinicalException.EncounterClosed();
        }
    }

    public EncounterClosure close(Clinician closedBy, Collection<SignedNote> notes, Set<UUID> voidedNoteIds, Clock clock) {
        requireOpen();
        boolean ready = notes.stream()
                .filter(note -> note.encounterId().equals(id))
                .filter(note -> !voidedNoteIds.contains(note.id()))
                .anyMatch(note -> type.requiresDischargeSummary() ? note.type() == NoteType.DISCHARGE : note.type() != NoteType.ADDENDUM);
        if (!ready) {
            throw new ClinicalException.EncounterNotReadyToClose(type.requiresDischargeSummary()
                    ? "requiere una epicrisis firmada y vigente"
                    : "requiere al menos una nota clínica firmada y vigente");
        }
        return new EncounterClosure(id, Instant.now(clock), closedBy);
    }
}
