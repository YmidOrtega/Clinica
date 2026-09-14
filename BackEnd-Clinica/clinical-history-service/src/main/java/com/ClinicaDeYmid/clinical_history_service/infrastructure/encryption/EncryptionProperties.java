package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties("clinica.clinical.encryption")
public record EncryptionProperties(@DefaultValue("clinical-kek") String transitKey, Map<String, String> retiredMasterKeys) {
}
