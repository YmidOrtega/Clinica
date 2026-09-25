package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NumberingResolutions {

    NumberingResolution save(NumberingResolution resolution);

    Optional<NumberingResolution> findByUuid(UUID uuid);

    Optional<NumberingResolution> findActive();

    Optional<NumberingResolution> lockActive();

    List<NumberingResolution> findAll();

    List<NumberingResolution> findOpenIn(DianEnvironment environment);

    List<NumberingResolution> findByPrefix(String prefix);
}
