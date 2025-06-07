package com.ClinicaDeYmid.suppliers_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TypeProvider {
    EPS("Entidad Promotora de Salud"),
    IPS("Institución Prestadora de Salud"),
    ARL("Administradora de Riesgos Laborales"),
    POLIZA_DE_SALUD("Póliza de Salud"),
    POLIZA_ESTUDIANTE("Póliza Estudiantil"),
    MEDICINA_PREPAGADA("Medicina Prepagada"),
    PLAN_COMPLEMENTARIO("Plan Complementario"),
    OTRO("Otro");

    private final String displayName;
}
