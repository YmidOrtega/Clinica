package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import java.time.Instant;
import java.util.UUID;

public interface AttachmentVault {

    void stage(UUID patientUuid, Attachment attachment, byte[] content);

    void archive(Attachment attachment, Instant retainUntil);

    void discardStaged(UUID attachmentId);

    byte[] openArchived(Attachment attachment);

    void extendRetention(UUID attachmentId, Instant retainUntil);
}
