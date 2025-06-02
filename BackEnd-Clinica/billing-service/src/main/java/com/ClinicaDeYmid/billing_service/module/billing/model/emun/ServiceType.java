package com.ClinicaDeYmid.module.billing.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ServiceType {
    EMERGENCY("Urgencias"),
    INTERNAL_MEDICINE("Medicina Interna"),
    SURGERY("Cirugía"),
    PEDIATRICS("Pediatría"),
    GYNECOLOGY("Ginecología"),
    ORTHOPEDICS("Ortopedia"),
    CARDIOLOGY("Cardiología"),
    ICU("UCI"),
    GENERAL_WARD("Hospitalización General");

    private final String displayName;
}
