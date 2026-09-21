package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.domain.Triage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class ClinicalEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    record ReflectedTriage(UUID admissionUuid, Triage.Level level, Instant at, UUID clinician) {
    }

    private ClinicalEventMapper() {
    }

    static Optional<ReflectedTriage> toTriage(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            if (!"ClinicalNoteSigned".equals(text(event, "type"))) {
                return Optional.empty();
            }
            JsonNode data = event.path("data");
            String level = text(data, "triageLevel");
            String admission = text(data, "admissionUuid");
            if (level == null || admission == null) {
                return Optional.empty();
            }
            return Optional.of(new ReflectedTriage(UUID.fromString(admission), Triage.Level.valueOf(level),
                    Instant.parse(required(event, "occurredAt")), clinician(data)));
        } catch (Exception ex) {
            throw new MalformedClinicalEventException("Clinical event does not follow clinical.encounters.v1", ex);
        }
    }

    private static UUID clinician(JsonNode data) {
        String author = text(data.path("author"), "uuid");
        return author == null ? null : UUID.fromString(author);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + field);
        }
        return value;
    }
}
