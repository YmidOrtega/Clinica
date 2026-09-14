package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DraftAttachment(UUID draftId, Attachment attachment, Instant uploadedAt) {

    public DraftAttachment {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
    }
}
