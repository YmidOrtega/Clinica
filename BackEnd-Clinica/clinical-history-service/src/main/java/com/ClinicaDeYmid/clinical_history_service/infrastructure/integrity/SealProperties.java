package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("clinica.clinical.seal")
public record SealProperties(Path keysLocation, String activeKeyId) {
}
