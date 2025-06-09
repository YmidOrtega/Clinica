package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción para errores de validación durante el registro
 */
public class HealthProviderValidationException extends HealthProviderException {

    private final String field;
    private final String invalidValue;

    public HealthProviderValidationException(String field, String invalidValue, String reason) {
        super(
                String.format("Error de validación en el campo '%s' con valor '%s': %s", field, invalidValue, reason),
                "HP_VALIDATION_ERROR",
                "CREATE_HEALTH_PROVIDER"
        );
        this.field = field;
        this.invalidValue = invalidValue;
    }

    public String getField() {
        return field;
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}
