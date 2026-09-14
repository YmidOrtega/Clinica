package com.ClinicaDeYmid.clinical_history_service.domain.clinician;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.time.Instant;
import java.util.Objects;

public record Signer(Clinician clinician, String email, Instant authenticatedAt) {

    public Signer {
        Objects.requireNonNull(clinician, "clinician");
        if (email == null || email.isBlank() || authenticatedAt == null) {
            throw new ClinicalException.SignerIdentityIncomplete();
        }
        email = email.strip();
    }
}
