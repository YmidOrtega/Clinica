package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface DocumentFileJpaRepository extends JpaRepository<DocumentFile, Long> {

    @Query("select f from DocumentFile f where f.document.uuid = :document and f.kind = :kind")
    Optional<DocumentFile> find(@Param("document") UUID document, @Param("kind") DocumentFile.Kind kind);
}
