package com.ClinicaDeYmid.admissions_service.application.patient;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;

public record UnidentifiedAdmissionRequest(PatientReference.Sex sex, int estimatedBirthYear, String description) {
}
