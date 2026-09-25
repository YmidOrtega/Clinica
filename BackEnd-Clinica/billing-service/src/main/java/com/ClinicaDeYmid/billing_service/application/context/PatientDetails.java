package com.ClinicaDeYmid.billing_service.application.context;

import java.time.LocalDate;
import java.util.UUID;

public sealed interface PatientDetails {

    UUID uuid();

    record Registered(UUID uuid, String documentType, String documentNumber, String firstNames, String lastNames,
                      LocalDate birthDate, String sex, String healthRegime) implements PatientDetails {
    }

    record Unidentified(UUID uuid, String code, String sex, int estimatedBirthYear) implements PatientDetails {
    }
}
