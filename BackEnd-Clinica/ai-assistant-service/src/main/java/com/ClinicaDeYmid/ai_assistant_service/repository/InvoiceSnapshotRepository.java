package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceSnapshotRepository extends JpaRepository<InvoiceSnapshot, Long> {

    Optional<InvoiceSnapshot> findByInvoiceUuid(UUID invoiceUuid);

    Optional<InvoiceSnapshot> findByNumber(String number);

    List<InvoiceSnapshot> findByStatus(String status);

    @Query("""
            SELECT i FROM InvoiceSnapshot i
            WHERE (:payerNit IS NULL OR i.payerNit = :payerNit)
              AND (:status IS NULL OR i.status = :status)
              AND (:dianStatus IS NULL OR i.dianStatus = :dianStatus)
              AND i.issuedOn BETWEEN :from AND :to
            ORDER BY i.issuedOn DESC, i.number DESC""")
    List<InvoiceSnapshot> search(@Param("payerNit") String payerNit, @Param("status") String status,
                                 @Param("dianStatus") String dianStatus, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to, Pageable pageable);
}
