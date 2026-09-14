package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DraftAttachments {

    void add(DraftAttachment attachment, UUID patientUuid);

    List<DraftAttachment> ofDraft(UUID draftId);

    Optional<DraftAttachment> find(UUID draftId, UUID attachmentId);

    void remove(UUID attachmentId);
}
