package com.ClinicaDeYmid.clinical_history_service.application.note;

import com.ClinicaDeYmid.clinical_history_service.application.integrity.RecordSealing;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Signer;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NotePolicy;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class NoteCommands {

    private static final Logger log = LoggerFactory.getLogger(NoteCommands.class);

    private final Encounters encounters;
    private final NoteDrafts drafts;
    private final ClinicalNotes notes;
    private final RecordSealing sealing;
    private final NotePolicy policy;
    private final Clock clock;

    public NoteCommands(Encounters encounters, NoteDrafts drafts, ClinicalNotes notes, RecordSealing sealing, NotePolicy policy,
                        Clock clock) {
        this.encounters = encounters;
        this.drafts = drafts;
        this.notes = notes;
        this.sealing = sealing;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public NoteDraft startDraft(UUID encounterId, NoteContent content, NoteRestriction restriction, Instant occurredAt, Clinician author) {
        Encounter encounter = encounters.find(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
        NoteDraft draft = NoteDraft.start(encounter, author, content, restriction, occurredAt, policy, clock);
        requireAmendable(draft, author);
        drafts.add(draft);
        return draft;
    }

    @Transactional
    public NoteDraft reviseDraft(UUID draftId, long expectedVersion, NoteContent content, NoteRestriction restriction, Instant occurredAt,
                                 Clinician editor) {
        NoteDraft draft = lockOwnDraft(draftId, expectedVersion, editor);
        Encounter encounter = encounters.find(draft.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        NoteDraft revised = draft.revise(editor, encounter, content, restriction, occurredAt, policy, clock);
        if (!drafts.replace(revised, expectedVersion)) {
            throw new ClinicalException.DraftVersionMismatch();
        }
        return revised;
    }

    @Transactional
    public void discardDraft(UUID draftId, long expectedVersion, Clinician author) {
        lockOwnDraft(draftId, expectedVersion, author);
        drafts.remove(draftId);
    }

    @Transactional
    public SignedNote sign(UUID draftId, long expectedVersion, Signer signer) {
        NoteDraft draft = lockOwnDraft(draftId, expectedVersion, signer.clinician());
        Encounter encounter = encounters.lock(draft.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        requireAmendable(draft, signer.clinician());
        SignedNote note = draft.sign(signer, encounter, policy, clock);
        notes.append(note);
        sealing.record(new LedgerEntry.NoteSigned(encounter.patientUuid(), note));
        drafts.remove(draftId);
        log.info("Note {} of type {} signed by {} in encounter {}{}", note.id(), note.type(), signer.clinician().uuid(), note.encounterId(),
                note.extemporaneous() ? " (extemporaneous)" : "");
        return note;
    }

    @Transactional
    public NoteVoid voidNote(UUID noteId, String reason, Clinician clinician) {
        SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
        Encounter encounter = encounters.lock(note.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        NoteVoid noteVoid = note.voidBy(clinician, encounter, reason, notes.voidOf(noteId).isPresent(), clock);
        if (!notes.addVoid(noteVoid)) {
            throw new ClinicalException.NoteAlreadyVoided();
        }
        sealing.record(new LedgerEntry.NoteVoided(encounter.patientUuid(), noteVoid));
        log.info("Note {} voided by {}", noteId, clinician.uuid());
        return noteVoid;
    }

    private NoteDraft lockOwnDraft(UUID draftId, long expectedVersion, Clinician clinician) {
        NoteDraft draft = drafts.lock(draftId).orElseThrow(ClinicalException.DraftNotFound::new);
        draft.requireAuthor(clinician);
        if (draft.version() != expectedVersion) {
            throw new ClinicalException.DraftVersionMismatch();
        }
        return draft;
    }

    private void requireAmendable(NoteDraft draft, Clinician clinician) {
        if (draft.content() instanceof NoteContent.Addendum addendum) {
            SignedNote amended = notes.find(addendum.amendsNoteId())
                    .orElseThrow(() -> new ClinicalException.InvalidAmendment("la nota aclarada no existe"));
            amended.requireAmendableBy(clinician, draft.encounterId(), notes.voidOf(amended.id()).isPresent());
        }
    }
}
