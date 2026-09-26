package com.ClinicaDeYmid.billing_service.support;

import java.util.UUID;

public final class ClinicalEvents {

    private static final String CHAIN = """
            {"sequence": 1, "entryHash": "%s", "previousHash": "%s", "keyId": "seal-2026"}"""
            .formatted("a".repeat(64), "0".repeat(64));

    private ClinicalEvents() {
    }

    public static String encounterOpened(UUID encounter, UUID admission, UUID patient, String openedAt) {
        return """
                {"eventId": "%s", "type": "EncounterOpened", "schemaVersion": 1, "occurredAt": "%s", "traceId": null,
                 "patientUuid": "%s", "encounterId": "%s",
                 "data": {"encounterType": "OUTPATIENT", "admissionUuid": "%s", "admissionVerified": true,
                          "openedBy": {"uuid": "%s", "role": "DOCTOR"},
                          "careSetting": {"serviceCode": "328", "modality": "01", "serviceGroup": "01"}},
                 "chain": %s}""".formatted(UUID.randomUUID(), openedAt, patient, encounter, admission, UUID.randomUUID(),
                CHAIN);
    }

    public static String noteSigned(UUID note, UUID encounter, UUID admission, UUID patient, String occurredAt,
                                    String code) {
        return """
                {"eventId": "%s", "type": "ClinicalNoteSigned", "schemaVersion": 1, "occurredAt": "%s", "traceId": null,
                 "patientUuid": "%s", "encounterId": "%s",
                 "data": {"noteId": "%s", "noteType": "CONSULTATION", "admissionUuid": "%s", "triageLevel": null,
                          "restricted": false, "author": {"uuid": "%s", "role": "DOCTOR"}, "careOccurredAt": "%s",
                          "extemporaneous": false,
                          "diagnoses": [{"code": "%s", "display": "Diagnóstico", "catalogVersion": "2021-02-08",
                                         "role": "PRINCIPAL", "type": "CONFIRMED_NEW"}],
                          "careReason": {"purpose": "15", "cause": "38"},
                          "amendsNoteId": null, "listChanges": 0, "vitalSigns": 0, "attachments": 0},
                 "chain": %s}""".formatted(UUID.randomUUID(), occurredAt, patient, encounter, note, admission,
                UUID.randomUUID(), occurredAt, code, CHAIN);
    }

    public static String noteVoided(UUID note, UUID patient, String occurredAt) {
        return """
                {"eventId": "%s", "type": "ClinicalNoteVoided", "schemaVersion": 1, "occurredAt": "%s", "traceId": null,
                 "patientUuid": "%s", "encounterId": null,
                 "data": {"noteId": "%s", "voidedBy": {"uuid": "%s", "role": "DOCTOR"}},
                 "chain": %s}""".formatted(UUID.randomUUID(), occurredAt, patient, note, UUID.randomUUID(), CHAIN);
    }
}
