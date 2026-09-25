package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaIssuers implements Issuers {

    private final IssuerJpaRepository repository;

    JpaIssuers(IssuerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Issuer save(Issuer issuer) {
        return repository.saveAndFlush(issuer);
    }

    @Override
    public Optional<Issuer> find() {
        return repository.findFirstByOrderByIdAsc();
    }
}
