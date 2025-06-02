package com.ClinicaDeYmid.module.patient.model.emun;

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
    OTRA("Otra");

    private final String displayName;

}
