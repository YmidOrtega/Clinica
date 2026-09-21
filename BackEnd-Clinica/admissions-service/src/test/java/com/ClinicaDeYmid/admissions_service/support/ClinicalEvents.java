package com.ClinicaDeYmid.admissions_service.support;

import java.time.Instant;
import java.util.UUID;

public final class ClinicalEvents {

    private ClinicalEvents() {
    }

    public static String triage(UUID patientUuid, UUID admissionUuid, String level, UUID clinician, Instant at) {
        return noteSigned(patientUuid, "TRIAGE", admissionUuid, "\"" + level + "\"", clinician, at);
    }

    public static String progressNote(UUID patientUuid, UUID admissionUuid, UUID clinician, Instant at) {
        return noteSigned(patientUuid, "PROGRESS", admissionUuid, "null", clinician, at);
    }

    private static String noteSigned(UUID patientUuid, String noteType, UUID admissionUuid, String level,
                                     UUID clinician, Instant at) {
        return """
                {"eventId": "%s", "type": "ClinicalNoteSigned", "schemaVersion": 1, "occurredAt": "%s",
                 "patientUuid": "%s", "encounterId": "%s",
                 "data": {"noteId": "%s", "noteType": "%s", "restricted": false,
                          "author": {"uuid": "%s", "role": "NURSE"}, "careOccurredAt": "%s",
                          "extemporaneous": false, "diagnoses": [], "listChanges": 0, "vitalSigns": 2,
                          "attachments": 0, "admissionUuid": %s, "triageLevel": %s},
                 "chain": {"sequence": 4, "entryHash": "%s", "previousHash": "%s", "keyId": "seal-2026"}}
                """.formatted(UUID.randomUUID(), at, patientUuid, UUID.randomUUID(), UUID.randomUUID(), noteType,
                clinician, at, admissionUuid == null ? "null" : "\"" + admissionUuid + "\"", level,
                "a".repeat(64), "b".repeat(64));
    }
}
