package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PractitionerFee;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFees;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JpaPractitionerFees implements PractitionerFees {

    private final PractitionerFeeJpaRepository repository;

    JpaPractitionerFees(PractitionerFeeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PractitionerFee> saveAll(List<PractitionerFee> fees) {
        List<PractitionerFee> saved = repository.saveAll(fees);
        repository.flush();
        return saved;
    }

    @Override
    public List<PractitionerFee> findBySale(UUID saleUuid) {
        return repository.findBySale(saleUuid);
    }

    @Override
    public List<PractitionerFee> findByPractitioner(UUID practitionerUuid, PractitionerFee.Status status, int limit) {
        return repository.findByPractitioner(practitionerUuid, status, PageRequest.of(0, limit));
    }
}
