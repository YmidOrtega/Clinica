package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.SubSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubSpecialtyRepository extends JpaRepository<SubSpecialty, Long> {

    @Query("select ss from SubSpecialty ss join fetch ss.specialty where ss.uuid = :uuid")
    Optional<SubSpecialty> findByUuid(@Param("uuid") UUID uuid);

    Optional<SubSpecialty> findByCode(String code);
}
