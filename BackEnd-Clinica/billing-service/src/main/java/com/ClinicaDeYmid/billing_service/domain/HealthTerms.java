package com.ClinicaDeYmid.billing_service.domain;

import java.util.regex.Pattern;

public record HealthTerms(PaymentModality modality, CoveragePlan coverage, String cucon, UncontractedCare uncontracted,
                          String policyNumber) {

    private static final Pattern CUCON = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern POLICY = Pattern.compile("^[A-Za-z0-9-]{1,30}$");

    public HealthTerms {
        DomainRules.required(modality, "modality");
        DomainRules.required(coverage, "coverage");
        if ((cucon == null) == (uncontracted == null)) {
            throw new IllegalArgumentException("A health invoice names either its CUCON or why it has no contract");
        }
        if (cucon != null && !CUCON.matcher(cucon).matches()) {
            throw new IllegalArgumentException("The CUCON has 64 hexadecimal characters");
        }
        if (policyNumber != null && policyNumber.isBlank()) {
            policyNumber = null;
        }
        if (coverage.requiresPolicy() && policyNumber == null) {
            throw new BillingException.InvalidData("policyNumber",
                    "es obligatorio con la cobertura " + coverage.label());
        }
        if (!coverage.requiresPolicy() && policyNumber != null) {
            throw new BillingException.InvalidData("policyNumber",
                    "solo se informa en coberturas SOAT o de planes voluntarios de salud");
        }
        if (policyNumber != null && !POLICY.matcher(policyNumber).matches()) {
            throw new BillingException.InvalidData("policyNumber", "admite hasta 30 letras, dígitos o guiones");
        }
    }

    public static HealthTerms contracted(PaymentModality modality, CoveragePlan coverage, String cucon) {
        return contracted(modality, coverage, cucon, null);
    }

    public static HealthTerms contracted(PaymentModality modality, CoveragePlan coverage, String cucon,
                                         String policyNumber) {
        return new HealthTerms(modality, coverage, cucon, null, policyNumber);
    }

    public static HealthTerms uncontracted(UncontractedCare reason, CoveragePlan coverage, String policyNumber) {
        DomainRules.required(reason, "reason");
        DomainRules.required(coverage, "coverage");
        if (reason == UncontractedCare.PRIVATE_PATIENT || coverage == CoveragePlan.PRIVATE) {
            throw new BillingException.InvalidData("uncontracted",
                    "no puede ser la atención particular: esa se factura al paciente");
        }
        return new HealthTerms(PaymentModality.EVENT, coverage, null, reason, policyNumber);
    }

    public static HealthTerms privatePatient() {
        return new HealthTerms(PaymentModality.EVENT, CoveragePlan.PRIVATE, null, UncontractedCare.PRIVATE_PATIENT, null);
    }

    public boolean billedWithoutContract() {
        return uncontracted != null && uncontracted != UncontractedCare.PRIVATE_PATIENT;
    }
}
