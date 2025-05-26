package com.ClinicaDeYmid.module.billing.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EntryRoute {
    EMERGENCY("Urgencias"),
    CONSULTATION("Consulta Externa"),
    REFERRAL("Remisión"),
    HOSPITALIZATION("Hospitalización"),
    SURGERY("Cirugía Programada");

    private final String displayName;
}
