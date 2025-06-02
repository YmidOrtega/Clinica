package com.ClinicaDeYmid.patient_service.module.patient.model.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IdentificationType {
    CEDULA_DE_CIUDADANIA("Cédula de Ciudadanía"),
    CEDULA_DE_EXTRANJERIA("Cédula de Extranjería"),
    TARJETA_DE_IDENTIDAD("Tarjeta de Identidad"),
    PASAPORTE("Pasaporte"),
    REGISTRO_CIVIL("Registro Civil"),
    PERMISO_ESPECIAL_DE_PERMANENCIA("Permiso Especial de Permanencia"),
    DOCUMENTO_NACIONAL_DE_IDENTIFICACION("Documento Nacional de Identificación");

    private final String displayName;
}
