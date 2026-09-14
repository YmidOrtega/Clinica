package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Signer;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record NoteDraft(
        UUID id,
        UUID encounterId,
        Clinician author,
        NoteContent content,
        NoteRestriction restriction,
        Instant occurredAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public NoteDraft {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static NoteDraft start(Encounter encounter, Clinician author, NoteContent content, NoteRestriction restriction,
                                  Instant occurredAt, NotePolicy policy, Clock clock) {
        ClinicalText.present(content, "content");
        requireAllowedAuthor(content.type(), author);
        if (content.type() != NoteType.ADDENDUM) {
            encounter.requireOpen();
        }
        if (content instanceof NoteContent.Addendum addendum && addendum.amendsNoteId() == null) {
            throw new ClinicalException.InvalidData("content.amendsNoteId", "es obligatorio");
        }
        Instant now = Instant.now(clock);
        Instant occurred = occurredAt == null ? now : occurredAt;
        policy.requireValidOccurrence(encounter, occurred, now);
        return new NoteDraft(UUID.randomUUID(), encounter.id(), author, content, restriction, occurred, 0, now, now);
    }

    public NoteType type() {
        return content.type();
    }

    public boolean isWrittenBy(Clinician clinician) {
        return author.isSamePersonAs(clinician);
    }

    public NoteDraft revise(Clinician editor, Encounter encounter, NoteContent revised, NoteRestriction revisedRestriction,
                            Instant occurredAt, NotePolicy policy, Clock clock) {
        requireAuthor(editor);
        ClinicalText.present(revised, "content");
        if (revised.type() != type() || !Objects.equals(amendedNoteOf(revised), amendedNoteOf(content))) {
            throw new ClinicalException.DraftTypeChange();
        }
        Instant now = Instant.now(clock);
        Instant occurred = occurredAt == null ? this.occurredAt : occurredAt;
        policy.requireValidOccurrence(encounter, occurred, now);
        return new NoteDraft(id, encounterId, author, revised, revisedRestriction, occurred, version + 1, createdAt, now);
    }

    public SignedNote sign(Signer signer, Encounter encounter, NotePolicy policy, Clock clock) {
        requireAuthor(signer.clinician());
        requireAllowedAuthor(type(), signer.clinician());
        if (type() != NoteType.ADDENDUM) {
            encounter.requireOpen();
        }
        List<String> missing = content.missingFields();
        if (!missing.isEmpty()) {
            throw new ClinicalException.NoteIncomplete(missing);
        }
        Instant recordedAt = Instant.now(clock);
        policy.requireRecentAuthentication(signer, recordedAt);
        policy.requireValidOccurrence(encounter, occurredAt, recordedAt);
        return new SignedNote(id, encounterId, signer.clinician(), signer.email(), content, restriction, occurredAt, recordedAt,
                policy.isExtemporaneous(occurredAt, recordedAt));
    }

    public void requireAuthor(Clinician clinician) {
        if (!isWrittenBy(clinician)) {
            throw new ClinicalException.DraftNotFound();
        }
    }

    private static UUID amendedNoteOf(NoteContent content) {
        return content instanceof NoteContent.Addendum addendum ? addendum.amendsNoteId() : null;
    }

    private static void requireAllowedAuthor(NoteType type, Clinician author) {
        if (!type.canBeWrittenBy(author.role())) {
            throw new ClinicalException.NoteTypeNotAllowed();
        }
    }
}
