package com.ClinicaDeYmid.clinical_history_service.domain.clinician;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.util.Arrays;

public enum ClinicalRole {
    DOCTOR,
    NURSE;

    public static ClinicalRole from(String role) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(role))
                .findFirst()
                .orElseThrow(ClinicalException.ClinicalRoleRequired::new);
    }
}
