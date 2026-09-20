package com.ClinicaDeYmid.admissions_service.application.patient;

import java.util.UUID;

public interface PatientRegistry {

    PatientLookup fetch(UUID uuid);
}
