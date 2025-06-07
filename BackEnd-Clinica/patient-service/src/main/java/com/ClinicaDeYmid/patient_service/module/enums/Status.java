package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    ALIVE("Vivo"),
    DECEASED("Muerto"),
    SUSPENDED("Suspendido"),
    DELETED("Eliminado");

    private final String displayName;

}
