package com.ClinicaDeYmid.admissions_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TriageLevel {
    RED("Rojo"),
    ORANGE("Naranja"),
    YELLOW("Amarillo"),
    GREEN("Verde"),
    BLUE("Azul");

    private final String displayName;
}
