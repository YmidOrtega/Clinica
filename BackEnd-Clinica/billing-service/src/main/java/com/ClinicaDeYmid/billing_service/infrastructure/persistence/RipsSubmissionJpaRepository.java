package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RipsSubmissionJpaRepository extends JpaRepository<RipsSubmission, Long> {

    @EntityGraph(attributePaths = {"invoice", "findings"})
    @Query("select s from RipsSubmission s where s.uuid = :uuid")
    Optional<RipsSubmission> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"invoice", "findings"})
    @Query("select s from RipsSubmission s where s.invoice.uuid = :invoice order by s.sequence desc")
    List<RipsSubmission> ofInvoice(@Param("invoice") UUID invoice);

    @Query("""
            select s.uuid from RipsSubmission s
            where s.status = com.ClinicaDeYmid.billing_service.domain.RipsSubmission.Status.PENDING
            order by s.lastAttemptAt, s.id""")
    List<UUID> pending(Pageable page);
}
