package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaDocumentFiles implements DocumentFiles {

    private final DocumentFileJpaRepository repository;

    JpaDocumentFiles(DocumentFileJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentFile save(DocumentFile file) {
        return repository.saveAndFlush(file);
    }

    @Override
    public Optional<DocumentFile> find(UUID documentUuid, DocumentFile.Kind kind) {
        return repository.find(documentUuid, kind);
    }
}
