package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface PatientJpaRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUuid(UUID uuid);

    @Query("select p from Patient p where p.document.type = :type and p.document.number = :number")
    Optional<Patient> findByDocument(@Param("type") DocumentType type, @Param("number") String number);

    @Query("select count(p) > 0 from Patient p where p.document.type = :type and p.document.number = :number")
    boolean existsByDocument(@Param("type") DocumentType type, @Param("number") String number);

    @Query("""
            select p from Patient p
            where p.name.lastNames like :lastNames
            order by p.name.lastNames, p.name.firstNames, p.id
            """)
    Page<Patient> searchByLastNames(@Param("lastNames") String lastNamesPattern, Pageable pageable);

    @Query("""
            select p from Patient p
            where p.name.lastNames like :lastNames and p.name.firstNames like :firstNames
            order by p.name.lastNames, p.name.firstNames, p.id
            """)
    Page<Patient> searchByFullName(@Param("lastNames") String lastNamesPattern, @Param("firstNames") String firstNamesPattern,
                                   Pageable pageable);
}
