package com.ClinicaDeYmid.module.patient.model.emun;

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
