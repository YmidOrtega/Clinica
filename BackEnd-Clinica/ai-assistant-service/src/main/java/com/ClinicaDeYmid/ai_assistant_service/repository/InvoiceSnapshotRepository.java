package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceSnapshotRepository extends JpaRepository<InvoiceSnapshot, Long> {

    Optional<InvoiceSnapshot> findByInvoiceUuid(UUID invoiceUuid);

    Optional<InvoiceSnapshot> findByNumber(String number);
}
