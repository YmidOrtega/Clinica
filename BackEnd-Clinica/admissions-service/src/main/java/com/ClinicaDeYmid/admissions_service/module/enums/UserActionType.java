package com.ClinicaDeYmid.admissions_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserActionType {
    CREATED("Creado"),
    UPDATED("Actualizado"),
    INVOICED("Facturado"),
    DISCHARGED("Dado de alta"),
    CANCELLED("Cancelado"),
    REACTIVATED("Reactivado");

    private final String displayName;
}
