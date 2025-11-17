package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AllergySeverity {
    MILD("Leve", "Síntomas leves que no requieren intervención médica inmediata"),
    MODERATE("Moderada", "Síntomas que pueden requerir medicación o supervisión médica"),
    SEVERE("Severa", "Síntomas graves que requieren atención médica inmediata"),
    LIFE_THREATENING("Amenaza vital", "Reacciones potencialmente mortales como anafilaxia");

    private final String displayName;
    private final String description;
}