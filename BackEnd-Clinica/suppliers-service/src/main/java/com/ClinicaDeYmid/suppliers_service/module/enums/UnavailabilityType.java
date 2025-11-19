package com.ClinicaDeYmid.suppliers_service.module.enums;

import lombok.Getter;

@Getter
public enum UnavailabilityType {
    VACATION("Vacaciones"),
    SICK_LEAVE("Incapacidad Médica"),
    MATERNITY_LEAVE("Licencia de Maternidad"),
    PATERNITY_LEAVE("Licencia de Paternidad"),
    TRAINING("Capacitación/Formación"),
    CONFERENCE("Conferencia/Congreso"),
    PERSONAL_LEAVE("Permiso Personal"),
    EMERGENCY("Emergencia"),
    SABBATICAL("Año Sabático"),
    OTHER("Otro");

    private final String displayName;

    UnavailabilityType(String displayName) {
        this.displayName = displayName;
    }
}