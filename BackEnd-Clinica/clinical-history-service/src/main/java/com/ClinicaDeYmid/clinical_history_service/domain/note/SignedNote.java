package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SignedNote(
        UUID id,
        UUID encounterId,
        Clinician author,
        String signerEmail,
        NoteContent content,
        NoteRestriction restriction,
        List<AppliedUpdate> updates,
        Instant occurredAt,
        Instant recordedAt,
        boolean extemporaneous) {

    public SignedNote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(signerEmail, "signerEmail");
        Objects.requireNonNull(content, "content");
        updates = updates == null ? List.of() : List.copyOf(updates);
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }

    public boolean isRestricted() {
        return restriction != null;
    }

    public NoteType type() {
        return content.type();
    }

    public Optional<UUID> amends() {
        return content instanceof NoteContent.Addendum addendum ? Optional.of(addendum.amendsNoteId()) : Optional.empty();
    }

    public void requireAmendableBy(Clinician clinician, UUID encounterId, boolean voided) {
        if (!this.encounterId.equals(encounterId)) {
            throw new ClinicalException.InvalidAmendment("la nota aclarada pertenece a otra atención");
        }
        if (voided) {
            throw new ClinicalException.InvalidAmendment("la nota aclarada está anulada");
        }
        if (author.role() != clinician.role()) {
            throw new ClinicalException.InvalidAmendment("solo un profesional del mismo perfil del autor puede aclararla");
        }
    }

    public NoteVoid voidBy(Clinician clinician, Encounter encounter, String reason, boolean alreadyVoided, Clock clock) {
        if (!author.isSamePersonAs(clinician)) {
            throw new ClinicalException.NotTheAuthor();
        }
        if (alreadyVoided) {
            throw new ClinicalException.NoteAlreadyVoided();
        }
        encounter.requireOpen();
        return new NoteVoid(id, ClinicalText.required(reason, "reason", ClinicalText.SHORT), clinician, Instant.now(clock));
    }
}
