package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByInvoiceUuidAndStatus(UUID invoiceUuid, FindingStatus status);

    List<Finding> findByInvoiceUuidOrderByDetectedAtDesc(UUID invoiceUuid);

    @Query("""
            SELECT f FROM Finding f
            WHERE f.status = :status
              AND (:severity IS NULL OR f.severity = :severity)
              AND (:rule IS NULL OR f.rule = :rule)
              AND (:invoiceNumber IS NULL OR f.invoiceNumber = :invoiceNumber)
            ORDER BY CASE f.severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
                     f.dueOn ASC NULLS LAST, f.detectedAt ASC""")
    Page<Finding> tray(@Param("status") FindingStatus status, @Param("severity") FindingRule.Severity severity,
                       @Param("rule") FindingRule rule, @Param("invoiceNumber") String invoiceNumber, Pageable pageable);

    @Query("SELECT f.rule, f.severity, count(f) FROM Finding f WHERE f.status = 'OPEN' GROUP BY f.rule, f.severity")
    List<Object[]> openByRule();
}
