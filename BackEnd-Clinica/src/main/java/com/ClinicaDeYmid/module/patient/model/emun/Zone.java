package com.ClinicaDeYmid.module.patient.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Zone {
    URBAN("Urbano"),
    RURAL("Rural");

    private final String description;

}
