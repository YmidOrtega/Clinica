package com.ClinicaDeYmid.patient_service.module.patient.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Disability {
    NONE("Ninguna"),
    VISUAL("Visual"),
    AUDITIVA("Auditiva"),
    FISICA("Física"),
    COGNITIVA("Cognitiva"),
    PSICOSOCIAL("Psicosocial"),
    MULTIPLE("Múltiple"),
    OTHER("Otra");

    private final String displayName;

}
