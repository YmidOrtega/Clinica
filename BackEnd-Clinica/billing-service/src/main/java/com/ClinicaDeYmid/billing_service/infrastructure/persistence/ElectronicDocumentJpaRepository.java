package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ElectronicDocumentJpaRepository extends JpaRepository<ElectronicDocument, Long> {

    @EntityGraph(attributePaths = {"invoice"})
    @Query("select d from ElectronicDocument d where d.uuid = :uuid")
    Optional<ElectronicDocument> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"invoice"})
    @Query("""
            select d from ElectronicDocument d
            where d.invoice.uuid = :invoice
              and d.type = com.ClinicaDeYmid.billing_service.domain.ElectronicDocument.Type.INVOICE""")
    Optional<ElectronicDocument> ofInvoice(@Param("invoice") UUID invoice);

    @EntityGraph(attributePaths = {"invoice", "creditNote"})
    @Query("select d from ElectronicDocument d where d.creditNote.uuid = :note")
    Optional<ElectronicDocument> ofCreditNote(@Param("note") UUID note);

    @Query("select d.uuid from ElectronicDocument d where d.signedAt is null order by d.issuedAt, d.id")
    List<UUID> awaitingSignature(Pageable page);

    @Query("""
            select d.uuid from ElectronicDocument d
            where d.signedAt is not null and d.dianStatus is null
            order by d.type, d.issuedAt, d.id""")
    List<UUID> awaitingDelivery(Pageable page);

    @Query("""
            select d.uuid from ElectronicDocument d
            where d.dianStatus = com.ClinicaDeYmid.billing_service.domain.DianStatus.AWAITING_VALIDATION
            order by d.dianStatusAt, d.id""")
    List<UUID> awaitingDianValidation(Pageable page);

    @Query("""
            select d.uuid from ElectronicDocument d
            where d.dianStatus = com.ClinicaDeYmid.billing_service.domain.DianStatus.ACCEPTED
              and exists (select f.id from DocumentFile f where f.document = d
                  and f.kind = com.ClinicaDeYmid.billing_service.domain.DocumentFile.Kind.DIAN_APPLICATION_RESPONSE)
              and not exists (select f.id from DocumentFile f where f.document = d
                  and f.kind = com.ClinicaDeYmid.billing_service.domain.DocumentFile.Kind.ATTACHED_DOCUMENT)
            order by d.dianStatusAt, d.id""")
    List<UUID> awaitingAttachment(Pageable page);
}
