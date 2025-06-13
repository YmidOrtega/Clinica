package com.ClinicaDeYmid.admissions_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TypeOfAuthorization {
    EMERGENCY_SERVICES("Servicios de Emergencia"),
    HOSPITALIZATION("Hospitalización"),
    AMBULATORY_SERVICES("Servicios Ambulatorios"),
    SPECIALIZED_SERVICES("Servicios Especializados"),
    MEDICATIONS("Medicamentos"),
    SPACE_TRANSPORT_SERVICES("Servicios de Traslado Espacial");

    private final String displayName;
}
