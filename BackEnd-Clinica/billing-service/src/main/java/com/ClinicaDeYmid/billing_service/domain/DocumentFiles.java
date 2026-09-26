package com.ClinicaDeYmid.billing_service.domain;

import java.util.Optional;
import java.util.UUID;

public interface DocumentFiles {

    DocumentFile save(DocumentFile file);

    Optional<DocumentFile> find(UUID documentUuid, DocumentFile.Kind kind);
}
