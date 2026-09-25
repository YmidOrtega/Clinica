package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record AdmissionSnapshot(
        UUID admissionUuid,
        String admissionNumber,
        long admissionVersion,
        UUID patientUuid,
        AdmissionKind kind,
        Status status,
        UUID configurationServiceUuid,
        Instant occurredAt,
        DischargeType discharge,
        String reason) {

    public enum Status {
        REGISTERED,
        ACTIVE,
        DISCHARGED,
        CANCELLED
    }

    private static final Pattern NUMBER = Pattern.compile("^ADM-[0-9]{4}-[0-9]{6}$");

    public AdmissionSnapshot {
        DomainRules.required(admissionUuid, "admissionUuid");
        admissionNumber = DomainRules.requiredPattern(admissionNumber, "admissionNumber", NUMBER,
                "debe tener la forma ADM-AAAA-NNNNNN");
        if (admissionVersion < 0) {
            throw new BillingException.InvalidData("admissionVersion", "no puede ser negativa");
        }
        DomainRules.required(patientUuid, "patientUuid");
        DomainRules.required(kind, "kind");
        DomainRules.required(status, "status");
        DomainRules.required(configurationServiceUuid, "configurationServiceUuid");
        DomainRules.required(occurredAt, "occurredAt");
        reason = DomainRules.optionalText(reason, "reason", 500);
    }
}
