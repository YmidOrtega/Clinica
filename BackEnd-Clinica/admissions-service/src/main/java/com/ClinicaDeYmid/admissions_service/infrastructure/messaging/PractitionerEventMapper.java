package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

final class PractitionerEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PractitionerEventMapper() {
    }

    static Optional<PractitionerReference> toReference(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode event = JSON.readTree(payload);
            JsonNode account = event.path("authUserUuid");
            return Optional.of(new PractitionerReference(
                    UUID.fromString(required(event, "practitionerUuid").asText()),
                    required(event, "version").asLong(),
                    required(event, "fullName").asText(),
                    required(required(event, "registration"), "number").asText(),
                    principalSpecialty(event),
                    required(required(event, "status"), "code").asText(),
                    account.isMissingNode() || account.isNull() ? null : UUID.fromString(account.asText())));
        } catch (MalformedPractitionerEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MalformedPractitionerEventException("Practitioner event does not follow practitioners.v1", ex);
        }
    }

    private static String principalSpecialty(JsonNode event) {
        JsonNode specialties = event.path("specialties");
        if (!specialties.isArray()) {
            return null;
        }
        for (JsonNode specialty : specialties) {
            if (specialty.path("principal").asBoolean()) {
                String name = specialty.path("specialtyName").asText();
                JsonNode sub = specialty.path("subSpecialtyName");
                return sub.isMissingNode() || sub.isNull() ? name : name + " · " + sub.asText();
            }
        }
        return null;
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new MalformedPractitionerEventException("Missing field " + field, null);
        }
        return value;
    }
}
