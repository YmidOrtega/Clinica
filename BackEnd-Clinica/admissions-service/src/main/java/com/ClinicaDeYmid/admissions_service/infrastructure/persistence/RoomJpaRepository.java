package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RoomJpaRepository extends JpaRepository<Room, Long> {

    @Query("select r from Room r join fetch r.location where r.uuid = :uuid")
    Optional<Room> findByUuid(@Param("uuid") UUID uuid);

    @Query("select r from Room r join fetch r.location l where r.name = :name and l.uuid = :locationUuid")
    Optional<Room> findByNameAndLocation(@Param("name") String name, @Param("locationUuid") UUID locationUuid);

    @Query("select r from Room r join fetch r.location l where l.uuid = :locationUuid order by r.name")
    List<Room> findByLocation(@Param("locationUuid") UUID locationUuid);
}
