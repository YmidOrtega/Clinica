package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class AdmissionEventMapper {

    static final Set<String> KNOWN_TYPES = Set.of("AdmissionRegistered", "AdmissionPhaseChanged",
            "AdmissionBedAssigned", "AdmissionBedReleased", "AdmissionCoveragePending", "AdmissionDischarged",
            "AdmissionCancelled");

    private static final ObjectMapper JSON = new ObjectMapper();

    private AdmissionEventMapper() {
    }

    static Optional<AdmissionSnapshot> toSnapshot(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            String type = required(event, "type").asText();
            if (!KNOWN_TYPES.contains(type)) {
                return Optional.empty();
            }
            JsonNode data = required(event, "data");
            JsonNode admission = required(data, "admission");
            return Optional.of(new AdmissionSnapshot(
                    UUID.fromString(required(event, "admissionUuid").asText()),
                    required(admission, "number").asText(),
                    required(event, "admissionVersion").asLong(),
                    UUID.fromString(required(admission, "patientUuid").asText()),
                    AdmissionKind.valueOf(required(admission, "kind").asText()),
                    AdmissionSnapshot.Status.valueOf(required(admission, "status").asText()),
                    UUID.fromString(required(admission, "configurationServiceUuid").asText()),
                    Instant.parse(required(event, "occurredAt").asText()),
                    optionalText(data, "discharge") == null ? null : DischargeType.valueOf(optionalText(data, "discharge")),
                    "AdmissionCancelled".equals(type) ? optionalText(data, "reason") : null));
        } catch (MalformedAdmissionEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MalformedAdmissionEventException("Admission event does not follow admissions.events.v1", ex);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new MalformedAdmissionEventException("Missing field " + field, null);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
