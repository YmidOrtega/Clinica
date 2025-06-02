package com.ClinicaDeYmid.patient_service.module.patient.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Gender {
    masculine("Masculino"),
    feminine("Femenino"),
    other("Otro"),
    notDisclosed("No declarado");
    private final String description;
}
