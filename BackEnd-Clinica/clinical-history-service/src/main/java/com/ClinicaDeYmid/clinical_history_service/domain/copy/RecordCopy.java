package com.ClinicaDeYmid.clinical_history_service.domain.copy;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecordCopy(
        UUID id,
        UUID patientUuid,
        UUID requestedBy,
        String requestedRole,
        String reason,
        Instant periodFrom,
        Instant periodTo,
        int entries,
        boolean chainVerified,
        String documentSha256,
        String keyId,
        String seal,
        Instant generatedAt) {

    public RecordCopy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedRole, "requestedRole");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(documentSha256, "documentSha256");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(seal, "seal");
        Objects.requireNonNull(generatedAt, "generatedAt");
    }

    public static String requireReason(String reason) {
        String text = ClinicalText.required(reason, "reason", ClinicalText.SHORT);
        if (text.length() < 10) {
            throw new ClinicalException.InvalidData("reason", "debe explicar la solicitud en al menos 10 caracteres");
        }
        return text;
    }

    public static void requirePeriod(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ClinicalException.InvalidData("from", "debe ser anterior a 'to'");
        }
    }
}
