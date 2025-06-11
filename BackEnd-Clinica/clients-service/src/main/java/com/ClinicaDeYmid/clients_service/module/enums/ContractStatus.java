package com.ClinicaDeYmid.clients_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContractStatus {
    ACTIVE("ACTIVO"),
    INACTIVE("INACTIVO"),
    PENDING("PENDIENTE"),
    EXPIRED("VENCIDO"),
    CANCELLED("CANCELADO"),
    SUSPENDED("SUSPENDIDO");

    private final String displayName;

}
