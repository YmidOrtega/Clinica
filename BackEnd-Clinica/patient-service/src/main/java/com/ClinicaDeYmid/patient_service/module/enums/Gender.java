package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Gender {
    MASCULINE("Masculino"),
    FEMININE("Femenino"),
    OTHER("Otro"),
    NOT_DISCLOSED("No declarado");

    private final String displayName;
}
