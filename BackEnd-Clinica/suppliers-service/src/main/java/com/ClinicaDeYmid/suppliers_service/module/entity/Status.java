package com.ClinicaDeYmid.suppliers_service.module.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    ACTIVE("Activo"),
    INACTIVE("Inactivo"),
    PENDING("Pendiente"),
    SUSPENDED("Suspendido");

    private final String displayName;


}
