package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TypeOfAffiliation {
    BENEFICIARY("Beneficiary"),
    CONTRIBUTOR("Contributor"),
    POLIZA("Póliza"),
    PREPAID_MEDICINE("Medicina Prepagada"),
    ARL("ARL"),
    SUBSIDIZED("Subsidized"),
    VOLUNTARY("Voluntary"),
    SPECIAL("Special"),
    NONE("None");

    private final String displayName;
}
