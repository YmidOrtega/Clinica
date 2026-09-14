package com.ClinicaDeYmid.clinical_history_service.application.attachment;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.AttachmentVault;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachments;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;

@Service
public class AttachmentQueries {

    public record Download(Attachment attachment, byte[] content) {
    }

    private final ClinicalNotes notes;
    private final NoteDrafts drafts;
    private final DraftAttachments draftAttachments;
    private final Encounters encounters;
    private final AttachmentVault vault;
    private final RecordAccess access;
    private final TransactionOperations transactions;

    public AttachmentQueries(ClinicalNotes notes, NoteDrafts drafts, DraftAttachments draftAttachments, Encounters encounters,
                             AttachmentVault vault, RecordAccess access, TransactionOperations transactions) {
        this.notes = notes;
        this.drafts = drafts;
        this.draftAttachments = draftAttachments;
        this.encounters = encounters;
        this.vault = vault;
        this.access = access;
        this.transactions = transactions;
    }

    public List<DraftAttachment> ofDraft(UUID draftId, Clinician author) {
        return transactions.execute(status -> {
            drafts.find(draftId).orElseThrow(ClinicalException.DraftNotFound::new).requireAuthor(author);
            return draftAttachments.ofDraft(draftId);
        });
    }

    public Download download(UUID noteId, UUID attachmentId, Clinician reader) {
        Attachment attachment = transactions.execute(status -> {
            SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
            Attachment found = note.attachments().stream().filter(candidate -> candidate.id().equals(attachmentId)).findFirst()
                    .orElseThrow(ClinicalException.AttachmentNotFound::new);
            UUID patientUuid = encounters.find(note.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new).patientUuid();
            access.requireNote(reader, patientUuid, note, AccessAction.READ_ATTACHMENT, attachmentId);
            return found;
        });
        return new Download(attachment, vault.openArchived(attachment));
    }
}
