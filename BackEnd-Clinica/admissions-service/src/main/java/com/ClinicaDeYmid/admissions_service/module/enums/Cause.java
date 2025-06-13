package com.ClinicaDeYmid.admissions_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Cause {
    ILLNESS("Enfermedad"),
    ACCIDENT("Accidente"),
    WORK_ACCIDENT("Accidente de Trabajo"),
    TRAFFIC_ACCIDENT("Accidente de Tránsito"),
    VIOLENCE("Violencia"),
    MATERNITY("Maternidad"),
    PREVENTION("Prevención"),
    CONTROL("Control"),
    EMERGENCY("Emergencia"),
    ROUTINE_CHECKUP("Chequeo de Rutina"),
    VACCINATION("Vacunación"),
    OTHER("Otro");
    private final String description;
}
