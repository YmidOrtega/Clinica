package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import lombok.Getter;

@Getter
public class CriticalAllergyException extends BaseException {

    private final Long patientId;
    private final String allergen;
    private final AllergySeverity severity;

    public CriticalAllergyException(String message, Long patientId, String allergen, AllergySeverity severity) {
        super(
                message,
                "CRITICAL_ALLERGY_WARNING",
                "MANAGE_CRITICAL_ALLERGY"
        );
        this.patientId = patientId;
        this.allergen = allergen;
        this.severity = severity;
    }
}