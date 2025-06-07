package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Zone {
    URBAN("Urbano"),
    RURAL("Rural");

    private final String displayName;

}
