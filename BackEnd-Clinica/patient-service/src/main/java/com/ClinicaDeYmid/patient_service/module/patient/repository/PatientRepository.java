package com.ClinicaDeYmid.patient_service.module.patient.repository;

import com.ClinicaDeYmid.patient_service.module.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository <Patient, Long> {


}
