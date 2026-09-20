package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.CatalogueStatus;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    @Query("select s from Specialty s left join fetch s.subSpecialties where s.uuid = :uuid")
    Optional<Specialty> findByUuid(@Param("uuid") UUID uuid);

    @Query("select s from Specialty s left join fetch s.subSpecialties where s.code = :code")
    Optional<Specialty> findByCode(@Param("code") String code);

    @Query("""
            select distinct s from Specialty s left join fetch s.subSpecialties
            where (:status is null or s.statusCode = :status)
              and (:name is null or lower(s.name) like lower(concat(:name, '%')))
            order by s.code
            """)
    List<Specialty> search(@Param("status") CatalogueStatus.Code status, @Param("name") String name);
}
