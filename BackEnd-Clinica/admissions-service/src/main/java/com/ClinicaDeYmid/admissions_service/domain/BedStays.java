package com.ClinicaDeYmid.admissions_service.domain;

import java.util.Optional;
import java.util.UUID;

public interface BedStays {

    BedStay save(BedStay stay);

    Optional<BedStay> findOpenByBed(UUID bedUuid);

    Optional<BedStay> findOpenByOccupant(UUID occupant);
}
