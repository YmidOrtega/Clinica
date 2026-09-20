package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Rooms {

    Room save(Room room);

    Optional<Room> findByUuid(UUID uuid);

    Optional<Room> findByNameAndLocation(String name, UUID locationUuid);

    List<Room> findByLocation(UUID locationUuid);
}
