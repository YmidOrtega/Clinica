package com.ClinicaDeYmid.patient_service.infra.exception;

import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import lombok.Getter;

@Getter
public class ResourceNotFoundException extends BaseException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", "FIND_RESOURCE");
        this.resourceType = "Unknown";
        this.resourceId = null;
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(
                String.format("%s no encontrado con ID: %s", resourceType, resourceId),
                "RESOURCE_NOT_FOUND",
                "FIND_" + resourceType.toUpperCase()
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(
                String.format("%s no encontrado con %s: %s", resourceType, fieldName, fieldValue),
                "RESOURCE_NOT_FOUND",
                "FIND_" + resourceType.toUpperCase()
        );
        this.resourceType = resourceType;
        this.resourceId = fieldValue;
    }
}