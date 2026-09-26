package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalEventMapperTest {

    private static final String PATIENT = "3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d";
    private static final String ENCOUNTER = "8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String ADMISSION = "11111111-2222-4333-8444-555555555555";

    @Test
    void readsTheCareSettingOfAnOpenedEncounter() {
        ClinicalFact fact = ClinicalEventMapper.toFact(event("EncounterOpened", ENCOUNTER, """
                {"encounterType": "OUTPATIENT", "admissionUuid": "%s", "admissionVerified": true,
                 "openedBy": {"uuid": "%s", "role": "DOCTOR"},
                 "careSetting": {"serviceCode": "328", "modality": "01", "serviceGroup": "01"}}""".formatted(ADMISSION, PATIENT)))
                .orElseThrow();

        assertThat(fact).isEqualTo(new ClinicalFact.EncounterOpened(UUID.fromString(ENCOUNTER), UUID.fromString(ADMISSION),
                UUID.fromString(PATIENT), "OUTPATIENT", java.time.Instant.parse("2026-09-14T15:10:00.123456Z"),
                new ClinicalFact.CareSetting("328", "01", "01")));
    }

    @Test
    void readsDiagnosesAndTheCareReasonOfASignedNote() {
        ClinicalFact.NoteSigned fact = (ClinicalFact.NoteSigned) ClinicalEventMapper.toFact(event("ClinicalNoteSigned", ENCOUNTER, """
                {"noteId": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e", "noteType": "CONSULTATION", "admissionUuid": "%s",
                 "restricted": false, "careOccurredAt": "2026-09-14T15:00:00Z",
                 "diagnoses": [{"code": "I10X", "display": "HTA", "catalogVersion": "2021", "role": "PRINCIPAL", "type": "CONFIRMED_NEW"},
                               {"code": "E119", "display": "DM", "catalogVersion": "2021", "role": "RELATED", "type": "IMPRESSION"}],
                 "careReason": {"purpose": "15", "cause": "38"}}""".formatted(ADMISSION))).orElseThrow();

        assertThat(fact.purpose()).isEqualTo("15");
        assertThat(fact.cause()).isEqualTo("38");
        assertThat(fact.diagnoses()).extracting(ClinicalFact.CodedDiagnosis::code).containsExactly("I10X", "E119");
        assertThat(fact.diagnoses().getFirst().principal()).isTrue();
    }

    @Test
    void readsNotesWithoutReasonVoidsAndClosuresAndIgnoresOtherTypes() {
        ClinicalFact.NoteSigned legacy = (ClinicalFact.NoteSigned) ClinicalEventMapper.toFact(event("ClinicalNoteSigned", ENCOUNTER, """
                {"noteId": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e", "noteType": "TRIAGE", "careOccurredAt": "2026-09-14T15:00:00Z",
                 "diagnoses": []}""")).orElseThrow();

        assertThat(legacy.purpose()).isNull();
        assertThat(legacy.admissionUuid()).isNull();
        assertThat(ClinicalEventMapper.toFact(event("ClinicalNoteVoided", null,
                "{\"noteId\": \"5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e\"}"))).get().isInstanceOf(ClinicalFact.NoteVoided.class);
        assertThat(ClinicalEventMapper.toFact(event("EncounterClosed", ENCOUNTER, "{}"))).get()
                .isInstanceOf(ClinicalFact.EncounterClosed.class);
        assertThat(ClinicalEventMapper.toFact(event("SomethingElse", ENCOUNTER, "{}"))).isEmpty();
        assertThatThrownBy(() -> ClinicalEventMapper.toFact(event("ClinicalNoteSigned", ENCOUNTER, "{}")))
                .isInstanceOf(MalformedClinicalEventException.class);
    }

    private static String event(String type, String encounter, String data) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "2026-09-14T15:10:00.123456Z",
                 "patientUuid": "%s", "encounterId": %s, "data": %s,
                 "chain": {"sequence": 1, "entryHash": "%s", "previousHash": "%s", "keyId": "seal-2026"}}"""
                .formatted(UUID.randomUUID(), type, PATIENT, encounter == null ? "null" : "\"" + encounter + "\"", data,
                        "a".repeat(64), "0".repeat(64));
    }
}
