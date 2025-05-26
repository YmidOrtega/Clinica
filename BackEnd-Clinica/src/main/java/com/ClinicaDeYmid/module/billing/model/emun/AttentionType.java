package com.ClinicaDeYmid.module.billing.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AttentionType {
    OUTPATIENT("Ambulatorio"),
    EMERGENCY("Urgencias"),
    HOSPITALIZATION("Hospitalización"),
    SURGERY("Cirugía"),
    CONSULTATION("Consulta"),
    PROCEDURE("Procedimiento");

    private final String displayName;
}
