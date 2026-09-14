package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NoteVoid(UUID noteId, String reason, Clinician voidedBy, Instant voidedAt) {

    public NoteVoid {
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(voidedBy, "voidedBy");
        Objects.requireNonNull(voidedAt, "voidedAt");
    }
}
