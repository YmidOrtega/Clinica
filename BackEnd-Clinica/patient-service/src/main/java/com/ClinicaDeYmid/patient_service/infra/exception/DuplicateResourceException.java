package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

@Getter
public class DuplicateResourceException extends BaseException {

    private final String resourceType;
    private final String duplicateField;
    private final Object duplicateValue;

    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE", "CREATE_RESOURCE");
        this.resourceType = "Unknown";
        this.duplicateField = null;
        this.duplicateValue = null;
    }

    public DuplicateResourceException(String resourceType, String duplicateField, Object duplicateValue) {
        super(
                String.format("%s ya existe con %s: %s", resourceType, duplicateField, duplicateValue),
                "DUPLICATE_RESOURCE",
                "CREATE_" + resourceType.toUpperCase()
        );
        this.resourceType = resourceType;
        this.duplicateField = duplicateField;
        this.duplicateValue = duplicateValue;
    }
}