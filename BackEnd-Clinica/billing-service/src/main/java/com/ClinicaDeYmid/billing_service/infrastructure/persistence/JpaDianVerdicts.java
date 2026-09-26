package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DianVerdict;
import com.ClinicaDeYmid.billing_service.domain.DianVerdicts;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JpaDianVerdicts implements DianVerdicts {

    private final DianVerdictJpaRepository repository;

    JpaDianVerdicts(DianVerdictJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DianVerdict save(DianVerdict verdict) {
        return repository.saveAndFlush(verdict);
    }

    @Override
    public List<DianVerdict> ofDocument(UUID documentUuid) {
        return repository.ofDocument(documentUuid);
    }
}
