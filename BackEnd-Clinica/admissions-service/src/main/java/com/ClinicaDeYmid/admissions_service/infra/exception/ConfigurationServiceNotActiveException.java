package com.ClinicaDeYmid.admissions_service.infra.exception;

public class ConfigurationServiceNotActiveException extends ValidationException {
    private final Long configurationServiceId;

    public ConfigurationServiceNotActiveException(Long configurationServiceId) {
        super("ConfigurationService with ID: " + configurationServiceId + " is not active");
        this.configurationServiceId = configurationServiceId;
    }

    public Long getConfigurationServiceId() {
        return configurationServiceId;
    }
}
