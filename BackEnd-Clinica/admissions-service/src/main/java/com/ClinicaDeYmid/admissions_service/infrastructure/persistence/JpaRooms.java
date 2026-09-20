package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.domain.Rooms;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaRooms implements Rooms {

    private final RoomJpaRepository repository;

    JpaRooms(RoomJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Room save(Room room) {
        return repository.saveAndFlush(room);
    }

    @Override
    public Optional<Room> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Room> findByNameAndLocation(String name, UUID locationUuid) {
        return repository.findByNameAndLocation(name, locationUuid);
    }

    @Override
    public List<Room> findByLocation(UUID locationUuid) {
        return repository.findByLocation(locationUuid);
    }
}
