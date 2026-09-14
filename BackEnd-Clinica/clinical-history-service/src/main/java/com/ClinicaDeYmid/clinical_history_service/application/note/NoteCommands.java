package com.ClinicaDeYmid.clinical_history_service.application.note;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.application.attachment.AttachmentRetention;
import com.ClinicaDeYmid.clinical_history_service.application.integrity.RecordSealing;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.AttachmentVault;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachments;
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
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemState;
import com.ClinicaDeYmid.clinical_history_service.domain.update.PatientChart;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NoteCommands {

    private static final Logger log = LoggerFactory.getLogger(NoteCommands.class);

    private final Encounters encounters;
    private final NoteDrafts drafts;
    private final ClinicalNotes notes;
    private final RecordSealing sealing;
    private final RecordAccess access;
    private final ClinicalCoding coding;
    private final PatientDirectory patients;
    private final PatientChart chart;
    private final DraftAttachments draftAttachments;
    private final AttachmentVault vault;
    private final AttachmentRetention retention;
    private final NotePolicy policy;
    private final Clock clock;

    public NoteCommands(Encounters encounters, NoteDrafts drafts, ClinicalNotes notes, RecordSealing sealing, RecordAccess access,
                        ClinicalCoding coding, PatientDirectory patients, PatientChart chart, DraftAttachments draftAttachments,
                        AttachmentVault vault, AttachmentRetention retention, NotePolicy policy, Clock clock) {
        this.encounters = encounters;
        this.drafts = drafts;
        this.notes = notes;
        this.sealing = sealing;
        this.access = access;
        this.coding = coding;
        this.patients = patients;
        this.chart = chart;
        this.draftAttachments = draftAttachments;
        this.vault = vault;
        this.retention = retention;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public NoteDraft startDraft(UUID encounterId, NoteContent content, NoteRestriction restriction, List<RecordUpdate> updates,
                                Instant occurredAt, Clinician author) {
        Encounter encounter = encounters.find(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
        NoteDraft draft = NoteDraft.start(encounter, author, coding.resolve(content), restriction, coding.resolve(updates), occurredAt, policy,
                clock);
        access.requireEncounter(author, encounter.patientUuid(), encounterId, AccessAction.WRITE_NOTE, draft.id());
        requireAmendable(draft, author);
        drafts.add(draft);
        return draft;
    }

    @Transactional
    public NoteDraft reviseDraft(UUID draftId, long expectedVersion, NoteContent content, NoteRestriction restriction,
                                 List<RecordUpdate> updates, Instant occurredAt, Clinician editor) {
        NoteDraft draft = lockOwnDraft(draftId, expectedVersion, editor);
        Encounter encounter = encounters.find(draft.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        access.requireEncounter(editor, encounter.patientUuid(), encounter.id(), AccessAction.WRITE_NOTE, draftId);
        NoteDraft revised = draft.revise(editor, encounter, coding.resolve(content), restriction, coding.resolve(updates), occurredAt, policy,
                clock);
        if (!drafts.replace(revised, expectedVersion)) {
            throw new ClinicalException.DraftVersionMismatch();
        }
        return revised;
    }

    @Transactional
    public void discardDraft(UUID draftId, long expectedVersion, Clinician author) {
        lockOwnDraft(draftId, expectedVersion, author);
        List<Attachment> staged = draftAttachments.ofDraft(draftId).stream().map(DraftAttachment::attachment).toList();
        drafts.remove(draftId);
        afterCommit(() -> staged.forEach(attachment -> vault.discardStaged(attachment.id())));
    }

    @Transactional
    public SignedNote sign(UUID draftId, long expectedVersion, Signer signer) {
        NoteDraft draft = lockOwnDraft(draftId, expectedVersion, signer.clinician());
        Encounter encounter = encounters.lock(draft.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        access.requireEncounter(signer.clinician(), encounter.patientUuid(), encounter.id(), AccessAction.WRITE_NOTE, draftId);
        requireAmendable(draft, signer.clinician());
        sealing.lockRecordOf(encounter.patientUuid());
        Map<UUID, ListItemState> listItems = chart.listItemsOf(patients.samePersonSubjects(encounter.patientUuid())).stream()
                .collect(Collectors.toMap(ListItemHistory::itemId, ListItemHistory::state));
        List<Attachment> attachments = draftAttachments.ofDraft(draftId).stream().map(DraftAttachment::attachment).toList();
        SignedNote note = draft.withResolvedContent(coding.resolve(draft.content()), coding.resolve(draft.updates()))
                .sign(signer, encounter, listItems, attachments, policy, clock);
        attachments.forEach(attachment -> vault.archive(attachment, retention.retainUntil(note.recordedAt())));
        notes.append(note);
        sealing.record(new LedgerEntry.NoteSigned(encounter.patientUuid(), note));
        drafts.remove(draftId);
        afterCommit(() -> attachments.forEach(attachment -> vault.discardStaged(attachment.id())));
        log.info("Note {} of type {} signed by {} in encounter {}{}", note.id(), note.type(), signer.clinician().uuid(), note.encounterId(),
                note.extemporaneous() ? " (extemporaneous)" : "");
        return note;
    }

    @Transactional
    public NoteVoid voidNote(UUID noteId, String reason, Clinician clinician) {
        SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
        Encounter encounter = encounters.lock(note.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
        access.requireEncounter(clinician, encounter.patientUuid(), encounter.id(), AccessAction.VOID_NOTE, noteId);
        NoteVoid noteVoid = note.voidBy(clinician, encounter, reason, notes.voidOf(noteId).isPresent(), clock);
        if (!notes.addVoid(noteVoid)) {
            throw new ClinicalException.NoteAlreadyVoided();
        }
        sealing.record(new LedgerEntry.NoteVoided(encounter.patientUuid(), noteVoid));
        log.info("Note {} voided by {}", noteId, clinician.uuid());
        return noteVoid;
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException cleanupFailed) {
                    log.warn("Could not remove staged attachments after commit; the staging purge will retry", cleanupFailed);
                }
            }
        });
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
