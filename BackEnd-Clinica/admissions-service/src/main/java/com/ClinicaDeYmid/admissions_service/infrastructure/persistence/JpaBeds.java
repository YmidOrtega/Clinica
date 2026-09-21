package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStatus;
import com.ClinicaDeYmid.admissions_service.domain.Beds;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaBeds implements Beds {

    private final BedJpaRepository repository;

    JpaBeds(BedJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Bed save(Bed bed) {
        return repository.saveAndFlush(bed);
    }

    @Override
    public Optional<Bed> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Bed> findByLabelAndRoom(String label, UUID roomUuid) {
        return repository.findByLabelAndRoom(label, roomUuid);
    }

    @Override
    public List<Bed> findByRoom(UUID roomUuid) {
        return repository.findByRoom(roomUuid);
    }

    @Override
    public List<Bed> findByLocation(UUID locationUuid) {
        return repository.findByLocation(locationUuid);
    }

    @Override
    public List<Bed> findAvailableInLocation(UUID locationUuid) {
        return repository.findByLocationAndStatus(locationUuid, BedStatus.Code.AVAILABLE);
    }

    @Override
    public Optional<Bed> lockByUuid(UUID uuid) {
        return repository.lockByUuid(uuid);
    }
}
