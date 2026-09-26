package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ClinicalEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ClinicalEventMapper() {
    }

    static Optional<ClinicalFact> toFact(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            JsonNode data = required(event, "data");
            return switch (required(event, "type").asText()) {
                case "EncounterOpened" -> {
                    JsonNode setting = data.path("careSetting");
                    yield Optional.of(new ClinicalFact.EncounterOpened(uuid(required(event, "encounterId")),
                            optionalUuid(data, "admissionUuid"), uuid(required(event, "patientUuid")),
                            required(data, "encounterType").asText(), instant(required(event, "occurredAt")),
                            setting.isMissingNode() || setting.isNull() ? null : new ClinicalFact.CareSetting(
                                    required(setting, "serviceCode").asText(), required(setting, "modality").asText(),
                                    required(setting, "serviceGroup").asText())));
                }
                case "ClinicalNoteSigned" -> {
                    JsonNode reason = data.path("careReason");
                    List<ClinicalFact.CodedDiagnosis> diagnoses = new ArrayList<>();
                    for (JsonNode diagnosis : data.path("diagnoses")) {
                        diagnoses.add(new ClinicalFact.CodedDiagnosis(required(diagnosis, "code").asText(),
                                required(diagnosis, "role").asText(), required(diagnosis, "type").asText()));
                    }
                    yield Optional.of(new ClinicalFact.NoteSigned(uuid(required(data, "noteId")),
                            uuid(required(event, "encounterId")), optionalUuid(data, "admissionUuid"),
                            required(data, "noteType").asText(), instant(required(data, "careOccurredAt")),
                            optionalText(reason, "purpose"), optionalText(reason, "cause"), diagnoses));
                }
                case "ClinicalNoteVoided" -> Optional.of(new ClinicalFact.NoteVoided(uuid(required(data, "noteId"))));
                case "EncounterClosed" -> Optional.of(new ClinicalFact.EncounterClosed(
                        uuid(required(event, "encounterId")), instant(required(event, "occurredAt"))));
                default -> Optional.empty();
            };
        } catch (MalformedClinicalEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MalformedClinicalEventException("Clinical event does not follow clinical.encounters.v1", ex);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new MalformedClinicalEventException("Missing field " + field, null);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : UUID.fromString(value);
    }

    private static UUID uuid(JsonNode value) {
        return UUID.fromString(value.asText());
    }

    private static Instant instant(JsonNode value) {
        return Instant.parse(value.asText());
    }
}
