package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.Payer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface PayerJpaRepository extends JpaRepository<Payer, Long> {

    Optional<Payer> findByUuid(UUID uuid);

    @Query("SELECT p FROM Payer p WHERE p.nit.number = :number")
    Optional<Payer> findByNitNumber(@Param("number") String number);

    @Query("SELECT COUNT(p) > 0 FROM Payer p WHERE p.nit.number = :number")
    boolean existsByNitNumber(@Param("number") String number);

    @Query("SELECT p FROM Payer p WHERE p.socialReason LIKE :prefix ORDER BY p.socialReason")
    Page<Payer> searchBySocialReason(@Param("prefix") String prefix, Pageable pageable);
}
