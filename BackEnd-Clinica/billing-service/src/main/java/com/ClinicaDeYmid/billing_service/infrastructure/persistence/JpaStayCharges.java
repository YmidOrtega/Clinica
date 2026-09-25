package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayCharges;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaStayCharges implements StayCharges {

    private final StayChargeJpaRepository repository;

    JpaStayCharges(StayChargeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public StayCharge save(StayCharge charge) {
        return repository.saveAndFlush(charge);
    }

    @Override
    public Optional<StayCharge> find(StayType stayType) {
        return repository.findByStayType(stayType);
    }

    @Override
    public List<StayCharge> findAll() {
        return repository.findAllOrdered();
    }
}
