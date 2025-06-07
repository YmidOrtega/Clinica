package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MaritalStatus {
    SINGLE("Soltero"),
    MARRIED("Casado"),
    DIVORCED("Divorciado"),
    WIDOWED("Viudo"),
    SEPARATED("Separado"),
    COHABITING("Unión libre"),
    NOT_DISCLOSED("No declarado");

    private final String displayName;
}
