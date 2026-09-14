package com.ClinicaDeYmid.clinical_history_service.application.attachment;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.AttachmentVault;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachments;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AttachmentCommands {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCommands.class);

    private final NoteDrafts drafts;
    private final Encounters encounters;
    private final DraftAttachments attachments;
    private final AttachmentVault vault;
    private final RecordAccess access;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AttachmentCommands(NoteDrafts drafts, Encounters encounters, DraftAttachments attachments, AttachmentVault vault, RecordAccess access,
                              TransactionOperations transactions, Clock clock) {
        this.drafts = drafts;
        this.encounters = encounters;
        this.attachments = attachments;
        this.vault = vault;
        this.access = access;
        this.transactions = transactions;
        this.clock = clock;
    }

    public DraftAttachment attach(UUID draftId, String fileName, byte[] content, Clinician author) {
        Attachment attachment = Attachment.inspect(fileName, content);
        UUID patientUuid = transactions.execute(status -> {
            NoteDraft draft = ownDraft(draftId, author);
            Encounter encounter = encounters.find(draft.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new);
            access.requireEncounter(author, encounter.patientUuid(), encounter.id(), AccessAction.WRITE_NOTE, attachment.id());
            if (attachments.ofDraft(draftId).size() >= Attachment.MAX_PER_NOTE) {
                throw new ClinicalException.TooManyAttachments(Attachment.MAX_PER_NOTE);
            }
            return encounter.patientUuid();
        });
        vault.stage(patientUuid, attachment, content);
        DraftAttachment staged = new DraftAttachment(draftId, attachment, Instant.now(clock));
        try {
            transactions.executeWithoutResult(status -> {
                ownDraft(draftId, author);
                attachments.add(staged, patientUuid);
            });
        } catch (RuntimeException failed) {
            vault.discardStaged(attachment.id());
            throw failed;
        }
        log.info("Attachment {} ({} bytes) staged for draft {}", attachment.id(), attachment.size(), draftId);
        return staged;
    }

    public void detach(UUID draftId, UUID attachmentId, Clinician author) {
        transactions.executeWithoutResult(status -> {
            ownDraft(draftId, author);
            attachments.find(draftId, attachmentId).orElseThrow(ClinicalException.AttachmentNotFound::new);
            attachments.remove(attachmentId);
        });
        vault.discardStaged(attachmentId);
    }

    private NoteDraft ownDraft(UUID draftId, Clinician author) {
        NoteDraft draft = drafts.find(draftId).orElseThrow(ClinicalException.DraftNotFound::new);
        draft.requireAuthor(author);
        return draft;
    }
}
