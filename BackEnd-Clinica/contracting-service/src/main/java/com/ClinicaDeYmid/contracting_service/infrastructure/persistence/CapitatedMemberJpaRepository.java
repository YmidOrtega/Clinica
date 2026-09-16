package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.CapitatedMember;
import com.ClinicaDeYmid.contracting_service.domain.MemberVerification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CapitatedMemberJpaRepository extends JpaRepository<CapitatedMember, Long> {

    @Query("SELECT m FROM CapitatedMember m JOIN FETCH m.contract c WHERE c.uuid = :contract AND m.period = :period "
            + "AND m.documentType = :type AND m.documentNumber = :number")
    Optional<CapitatedMember> findMember(@Param("contract") UUID contract, @Param("period") LocalDate period,
                                         @Param("type") String type, @Param("number") String number);

    @Query("SELECT m FROM CapitatedMember m JOIN FETCH m.contract c WHERE c.uuid = :contract AND m.period = :period "
            + "ORDER BY m.documentNumber")
    List<CapitatedMember> findByPeriod(@Param("contract") UUID contract, @Param("period") LocalDate period, Pageable pageable);

    @Query("SELECT m FROM CapitatedMember m JOIN FETCH m.contract c WHERE c.uuid = :contract AND m.period = :period "
            + "AND m.verification = :verification ORDER BY m.id")
    List<CapitatedMember> findByVerification(@Param("contract") UUID contract, @Param("period") LocalDate period,
                                             @Param("verification") MemberVerification verification, Pageable pageable);

    @Query("SELECT COUNT(m) FROM CapitatedMember m WHERE m.contract.uuid = :contract AND m.period = :period")
    long countByPeriod(@Param("contract") UUID contract, @Param("period") LocalDate period);

    @Query("SELECT m FROM CapitatedMember m JOIN FETCH m.contract c JOIN FETCH c.payer "
            + "WHERE m.documentType = :type AND m.documentNumber = :number AND m.period = :period")
    List<CapitatedMember> findCoverage(@Param("type") String type, @Param("number") String number,
                                       @Param("period") LocalDate period);
}
