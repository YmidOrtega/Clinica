package com.ClinicaDeYmid.clinical_history_service.support;

import java.util.UUID;

public final class PatientEvents {

    private PatientEvents() {
    }

    public static String registered(UUID uuid, long version, String type, String status, String dateOfDeath) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "2026-09-13T15:00:00Z",
                 "patientUuid": "%s", "patientVersion": %d,
                 "data": {"patient": {"uuid": "%s", "document": {"type": "CEDULA_DE_CIUDADANIA", "number": "1098765432"},
                          "firstNames": "Ana María", "lastNames": "Restrepo Gómez", "birthDate": "1990-04-12", "sex": "FEMALE",
                          "status": "%s"%s,
                          "affiliation": {"regime": "CONTRIBUTORY", "healthProviderNit": "900123456-7"}}}}
                """.formatted(UUID.randomUUID(), type, uuid, version, uuid, status,
                dateOfDeath == null ? "" : ", \"dateOfDeath\": \"" + dateOfDeath + "\"");
    }

    public static String registered(UUID uuid, long version) {
        return registered(uuid, version, "PatientRegistered", "ACTIVE", null);
    }

    public static String unidentified(UUID uuid, long version, String type, String status, UUID identifiedAs, UUID previous) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "2026-09-13T15:00:00Z",
                 "patientUuid": "%s", "patientVersion": %d,
                 "data": {"unidentifiedPatient": {"uuid": "%s", "code": "NN-2026-000042", "sex": "MALE",
                          "estimatedBirthYear": 1980, "status": "%s"%s}%s}}
                """.formatted(UUID.randomUUID(), type, uuid, version, uuid, status,
                identifiedAs == null ? "" : ", \"identifiedPatientUuid\": \"" + identifiedAs + "\"",
                previous == null ? "" : ", \"previousPatientUuid\": \"" + previous + "\"");
    }
}
