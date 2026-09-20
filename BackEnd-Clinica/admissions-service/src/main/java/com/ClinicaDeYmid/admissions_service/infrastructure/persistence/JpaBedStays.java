package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.BedStay;
import com.ClinicaDeYmid.admissions_service.domain.BedStays;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaBedStays implements BedStays {

    private final BedStayJpaRepository repository;

    JpaBedStays(BedStayJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public BedStay save(BedStay stay) {
        return repository.saveAndFlush(stay);
    }

    @Override
    public Optional<BedStay> findOpenByBed(UUID bedUuid) {
        return repository.findOpenByBed(bedUuid);
    }

    @Override
    public Optional<BedStay> findOpenByOccupant(UUID occupant) {
        return repository.findOpenByOccupant(occupant);
    }
}
