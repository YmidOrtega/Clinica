package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("clinica.clinical.encryption")
public record EncryptionProperties(Path keysLocation, String activeKeyId) {
}
