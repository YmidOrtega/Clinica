package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustment;
import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustments;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JpaPatientShareAdjustments implements PatientShareAdjustments {

    private final PatientShareAdjustmentJpaRepository repository;

    JpaPatientShareAdjustments(PatientShareAdjustmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PatientShareAdjustment save(PatientShareAdjustment adjustment) {
        return repository.saveAndFlush(adjustment);
    }

    @Override
    public List<PatientShareAdjustment> findByAccount(UUID accountUuid) {
        return repository.findByAccount(accountUuid);
    }
}
