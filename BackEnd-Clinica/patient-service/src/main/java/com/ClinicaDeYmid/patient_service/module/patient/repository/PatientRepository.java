package com.ClinicaDeYmid.patient_service.module.patient.repository;

import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository <Patient, Long> {

    Optional<Patient> findByIdentification(String identification);
}
