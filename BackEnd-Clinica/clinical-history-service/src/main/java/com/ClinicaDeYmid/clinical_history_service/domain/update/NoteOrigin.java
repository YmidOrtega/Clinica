package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;

import java.time.Instant;
import java.util.UUID;

public record NoteOrigin(UUID noteId, UUID encounterId, Clinician author, NoteRestriction restriction, Instant recordedAt, boolean voided) {

    public boolean isRestricted() {
        return restriction != null;
    }
}
