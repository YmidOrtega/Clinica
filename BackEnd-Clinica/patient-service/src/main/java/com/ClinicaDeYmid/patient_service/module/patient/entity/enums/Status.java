package com.ClinicaDeYmid.patient_service.module.patient.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    ALIVE("Vivo"),
    DECEASED("Fallecido"),
    SUSPENDED("Suspendido"),
    DELETED("Eliminado");

    private final String displayName;

}
