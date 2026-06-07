package com.ClinicaDeYmid.billing_service.module.config.repository;

import com.ClinicaDeYmid.billing_service.module.config.entity.DianResolution;
import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DianResolutionRepository extends JpaRepository<DianResolution, Long> {

    Optional<DianResolution> findByResolutionNumber(String resolutionNumber);

    boolean existsByResolutionNumber(String resolutionNumber);

    Optional<DianResolution> findByDocumentTypeAndActiveTrue(DocumentType documentType);

    List<DianResolution> findByActiveTrueOrderByDocumentTypeAsc();

    @Query("""
            SELECT r FROM DianResolution r
            WHERE r.documentType = :documentType
            AND r.active = true
            AND r.validTo >= :today
            AND r.currentConsecutive <= r.toNumber
            """)
    Optional<DianResolution> findValidResolution(
            @Param("documentType") DocumentType documentType,
            @Param("today") LocalDate today);

    @Query("""
            SELECT r FROM DianResolution r
            WHERE r.active = true
            AND r.validTo < :today
            ORDER BY r.validTo ASC
            """)
    List<DianResolution> findExpired(@Param("today") LocalDate today);
}
