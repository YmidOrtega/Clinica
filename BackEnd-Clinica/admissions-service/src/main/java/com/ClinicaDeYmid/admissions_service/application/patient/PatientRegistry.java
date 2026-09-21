package com.ClinicaDeYmid.admissions_service.application.patient;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;

import java.time.LocalDate;
import java.util.UUID;

public interface PatientRegistry {

    PatientLookup fetch(UUID uuid);

    PatientReference.Unidentified registerUnidentified(PatientReference.Sex sex, int estimatedBirthYear,
                                                       String description);

    DeathReport recordDeath(UUID patientUuid, LocalDate dateOfDeath);
}
