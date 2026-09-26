package com.ClinicaDeYmid.billing_service.application.clinical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface ClinicalFact {

    record EncounterOpened(UUID encounterId, UUID admissionUuid, UUID patientUuid, String encounterType,
                           Instant openedAt, CareSetting careSetting) implements ClinicalFact {
    }

    record NoteSigned(UUID noteId, UUID encounterId, UUID admissionUuid, String noteType, Instant careOccurredAt,
                      String purpose, String cause, List<CodedDiagnosis> diagnoses) implements ClinicalFact {

        public NoteSigned {
            diagnoses = diagnoses == null ? List.of() : List.copyOf(diagnoses);
        }
    }

    record NoteVoided(UUID noteId) implements ClinicalFact {
    }

    record EncounterClosed(UUID encounterId, Instant closedAt) implements ClinicalFact {
    }

    record CareSetting(String serviceCode, String modality, String serviceGroup) {
    }

    record CodedDiagnosis(String code, String role, String type) {

        public boolean principal() {
            return "PRINCIPAL".equals(role);
        }
    }
}
