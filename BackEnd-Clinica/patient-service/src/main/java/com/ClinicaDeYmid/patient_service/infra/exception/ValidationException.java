package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ValidationException extends BaseException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", "VALIDATE_INPUT");
        this.fieldErrors = new HashMap<>();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message, "VALIDATION_ERROR", "VALIDATE_INPUT");
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
    }

    public ValidationException(String field, String errorMessage) {
        super("Error de validación en el campo: " + field, "VALIDATION_ERROR", "VALIDATE_INPUT");
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, errorMessage);
    }

    public void addFieldError(String field, String message) {
        this.fieldErrors.put(field, message);
    }
}