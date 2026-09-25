package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Issuer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface IssuerJpaRepository extends JpaRepository<Issuer, Long> {

    Optional<Issuer> findFirstByOrderByIdAsc();
}
