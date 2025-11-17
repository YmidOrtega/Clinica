package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BloodType {
    A_POSITIVE("A+", "A positivo"),
    A_NEGATIVE("A-", "A negativo"),
    B_POSITIVE("B+", "B positivo"),
    B_NEGATIVE("B-", "B negativo"),
    AB_POSITIVE("AB+", "AB positivo"),
    AB_NEGATIVE("AB-", "AB negativo"),
    O_POSITIVE("O+", "O positivo"),
    O_NEGATIVE("O-", "O negativo"),
    UNKNOWN("Desconocido", "Tipo de sangre desconocido");

    private final String code;
    private final String displayName;
}