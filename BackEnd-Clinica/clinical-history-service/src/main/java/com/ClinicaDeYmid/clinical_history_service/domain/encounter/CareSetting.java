package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.util.regex.Pattern;

public record CareSetting(String serviceCode, String modality, String serviceGroup) {

    private static final Pattern SERVICE = Pattern.compile("^[0-9]{3,4}$");
    private static final Pattern MODALITY = Pattern.compile("^0[1-9]$");
    private static final Pattern GROUP = Pattern.compile("^0[1-5]$");

    public CareSetting {
        serviceCode = required(serviceCode, "careSetting.serviceCode", SERVICE);
        modality = required(modality, "careSetting.modality", MODALITY);
        serviceGroup = required(serviceGroup, "careSetting.serviceGroup", GROUP);
    }

    private static String required(String value, String field, Pattern pattern) {
        String trimmed = value == null ? null : value.strip();
        if (trimmed == null || !pattern.matcher(trimmed).matches()) {
            throw new ClinicalException.InvalidData(field, "no es un código válido de las tablas de referencia de RIPS");
        }
        return trimmed;
    }
}
