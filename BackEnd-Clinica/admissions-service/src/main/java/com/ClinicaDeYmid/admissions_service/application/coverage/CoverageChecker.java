package com.ClinicaDeYmid.admissions_service.application.coverage;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;

import java.time.LocalDate;

public interface CoverageChecker {

    CoverageVerdict check(PatientReference patient, LocalDate on);
}
