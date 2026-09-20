package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import com.ClinicaDeYmid.clinical_history_service.domain.practitioner.PractitionerReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

final class PractitionerEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PractitionerEventMapper() {
    }

    static Optional<PractitionerReference> toReference(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            JsonNode account = event.path("authUserUuid");
            if (account.isMissingNode() || account.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new PractitionerReference(
                    UUID.fromString(account.asText()),
                    UUID.fromString(required(event, "practitionerUuid").asText()),
                    required(event, "version").asLong(),
                    required(event, "fullName").asText(),
                    required(required(event, "registration"), "number").asText(),
                    principalSpecialty(event),
                    required(required(event, "status"), "code").asText(),
                    true));
        } catch (MalformedPractitionerEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MalformedPractitionerEventException("Practitioner event does not follow practitioners.v1", ex);
        }
    }

    static Optional<UUID> practitionerWithoutAccount(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            return Optional.of(UUID.fromString(required(event, "practitionerUuid").asText()));
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
                return specialty.path("subSpecialtyName").isMissingNode()
                        ? specialty.path("specialtyName").asText()
                        : specialty.path("specialtyName").asText() + " · " + specialty.path("subSpecialtyName").asText();
            }
        }
        return null;
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new MalformedPractitionerEventException("Practitioner event is missing '" + field + "'", null);
        }
        return value;
    }

    static final class MalformedPractitionerEventException extends RuntimeException {
        MalformedPractitionerEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
