package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DiseaseSeverity {
    CONTROLLED("Controlada", "Enfermedad bajo control con tratamiento adecuado"),
    PARTIALLY_CONTROLLED("Parcialmente controlada", "Enfermedad con control parcial, requiere ajustes"),
    UNCONTROLLED("No controlada", "Enfermedad sin control adecuado"),
    CRITICAL("Crítica", "Estado crítico que requiere atención inmediata"),
    IN_REMISSION("En remisión", "Sin síntomas activos actualmente");

    private final String displayName;
    private final String description;
}