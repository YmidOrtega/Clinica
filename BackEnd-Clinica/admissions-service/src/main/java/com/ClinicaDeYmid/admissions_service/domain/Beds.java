package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Beds {

    Bed save(Bed bed);

    Optional<Bed> findByUuid(UUID uuid);

    Optional<Bed> lockByUuid(UUID uuid);

    Optional<Bed> findByLabelAndRoom(String label, UUID roomUuid);

    List<Bed> findByRoom(UUID roomUuid);

    List<Bed> findAvailableInLocation(UUID locationUuid);
}
