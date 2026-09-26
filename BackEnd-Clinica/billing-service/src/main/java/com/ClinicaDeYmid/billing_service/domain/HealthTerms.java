package com.ClinicaDeYmid.billing_service.domain;

import java.util.regex.Pattern;

public record HealthTerms(PaymentModality modality, CoveragePlan coverage, String cucon, UncontractedCare uncontracted) {

    private static final Pattern CUCON = Pattern.compile("^[0-9a-f]{64}$");

    public HealthTerms {
        DomainRules.required(modality, "modality");
        DomainRules.required(coverage, "coverage");
        if ((cucon == null) == (uncontracted == null)) {
            throw new IllegalArgumentException("A health invoice names either its CUCON or why it has no contract");
        }
        if (cucon != null && !CUCON.matcher(cucon).matches()) {
            throw new IllegalArgumentException("The CUCON has 64 hexadecimal characters");
        }
    }

    public static HealthTerms contracted(PaymentModality modality, CoveragePlan coverage, String cucon) {
        return new HealthTerms(modality, coverage, cucon, null);
    }

    public static HealthTerms privatePatient() {
        return new HealthTerms(PaymentModality.EVENT, CoveragePlan.PRIVATE, null, UncontractedCare.PRIVATE_PATIENT);
    }
}
