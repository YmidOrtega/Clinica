package com.ClinicaDeYmid.admissions_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AttentionStatus {
   CREATED("Creado"),
    IN_PROGRESS("En progreso"),
    DISCHARGED("Alta"),
    CANCELLED("Cancelado");

    private final String displayName;
}
