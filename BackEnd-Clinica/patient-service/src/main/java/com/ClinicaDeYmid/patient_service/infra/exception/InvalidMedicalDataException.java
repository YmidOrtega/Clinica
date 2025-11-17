package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class InvalidMedicalDataException extends BaseException {

    private final String dataType;
    private final Map<String, String> invalidFields;

    public InvalidMedicalDataException(String message, String dataType) {
        super(
                message,
                "INVALID_MEDICAL_DATA",
                "VALIDATE_" + dataType.toUpperCase()
        );
        this.dataType = dataType;
        this.invalidFields = new HashMap<>();
    }

    public InvalidMedicalDataException(String message, String dataType, Map<String, String> invalidFields) {
        super(
                message,
                "INVALID_MEDICAL_DATA",
                "VALIDATE_" + dataType.toUpperCase()
        );
        this.dataType = dataType;
        this.invalidFields = invalidFields != null ? invalidFields : new HashMap<>();
    }

    public void addInvalidField(String field, String reason) {
        this.invalidFields.put(field, reason);
    }
}