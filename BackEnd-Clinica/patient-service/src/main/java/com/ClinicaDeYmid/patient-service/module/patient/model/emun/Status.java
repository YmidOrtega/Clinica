package com.ClinicaDeYmid.module.patient.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    ALIVE("Vivo"),
    DECEASED("Fallecido"),
    SUSPENDED("Suspendido"),
    DELETED("Eliminado");
    private final String description;

}
