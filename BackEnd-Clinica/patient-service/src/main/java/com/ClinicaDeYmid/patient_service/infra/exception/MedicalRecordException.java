package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

@Getter
public class MedicalRecordException extends BaseException {

    private final String recordType;
    private final Long patientId;

    public MedicalRecordException(String message, String recordType, Long patientId) {
        super(
                message,
                "MEDICAL_RECORD_ERROR",
                "MANAGE_" + recordType.toUpperCase()
        );
        this.recordType = recordType;
        this.patientId = patientId;
    }

    public MedicalRecordException(String message, String recordType, Long patientId, Throwable cause) {
        super(
                message,
                "MEDICAL_RECORD_ERROR",
                "MANAGE_" + recordType.toUpperCase(),
                cause
        );
        this.recordType = recordType;
        this.patientId = patientId;
    }
}