package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

final class PatientEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PatientEventMapper() {
    }

    static Optional<PatientReference> toReference(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            String type = required(event, "type").asText();
            long version = required(event, "patientVersion").asLong();
            if (type.startsWith("UnidentifiedPatient")) {
                return Optional.of(unidentified(required(event.path("data"), "unidentifiedPatient"), version));
            }
            if (type.startsWith("Patient")) {
                return Optional.of(registered(required(event.path("data"), "patient"), version));
            }
            return Optional.empty();
        } catch (MalformedPatientEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MalformedPatientEventException("Patient event does not follow patient.events.v1", ex);
        }
    }

    private static PatientReference.Registered registered(JsonNode patient, long version) {
        JsonNode document = required(patient, "document");
        JsonNode affiliation = required(patient, "affiliation");
        return new PatientReference.Registered(
                UUID.fromString(required(patient, "uuid").asText()),
                version,
                new PatientReference.Document(required(document, "type").asText(), required(document, "number").asText()),
                required(patient, "firstNames").asText(),
                required(patient, "lastNames").asText(),
                LocalDate.parse(required(patient, "birthDate").asText()),
                PatientReference.Sex.valueOf(required(patient, "sex").asText()),
                PatientReference.Registered.Status.valueOf(required(patient, "status").asText()),
                optionalDate(patient, "dateOfDeath"),
                required(affiliation, "regime").asText(),
                optionalText(affiliation, "healthProviderNit"));
    }

    private static PatientReference.Unidentified unidentified(JsonNode patient, long version) {
        String identified = optionalText(patient, "identifiedPatientUuid");
        return new PatientReference.Unidentified(
                UUID.fromString(required(patient, "uuid").asText()),
                version,
                required(patient, "code").asText(),
                PatientReference.Sex.valueOf(required(patient, "sex").asText()),
                required(patient, "estimatedBirthYear").asInt(),
                PatientReference.Unidentified.Status.valueOf(required(patient, "status").asText()),
                identified == null ? null : UUID.fromString(identified),
                optionalDate(patient, "dateOfDeath"));
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new MalformedPatientEventException("Missing field " + field, null);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static LocalDate optionalDate(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
