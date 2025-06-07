package com.ClinicaDeYmid.patient_service.module.patient.repository;

import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository <Patient, Long> {

    Optional<Patient> findByIdentificationNumber(String identificationNumber);

    @Query("""
    SELECT p FROM Patient p
    WHERE LOWER(CONCAT(p.name, ' ', p.lastName)) LIKE %:search%
       OR LOWER(p.name) LIKE %:search%
       OR LOWER(p.lastName) LIKE %:search%
       OR LOWER(p.identificationNumber) LIKE %:search%
""")
    Page<Patient> searchPatients(@Param("search") String search, Pageable pageable);

    boolean existsByIdentificationNumber(@NotBlank String s);
}
