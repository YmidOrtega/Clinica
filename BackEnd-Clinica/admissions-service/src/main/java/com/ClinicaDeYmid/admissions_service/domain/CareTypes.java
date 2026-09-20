package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareTypes {

    CareType save(CareType careType);

    Optional<CareType> findByUuid(UUID uuid);

    Optional<CareType> findByNameAndServiceType(String name, UUID serviceTypeUuid);

    List<CareType> findByServiceType(UUID serviceTypeUuid);
}
