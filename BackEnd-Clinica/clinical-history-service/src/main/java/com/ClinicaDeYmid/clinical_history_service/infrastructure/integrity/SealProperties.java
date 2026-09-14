package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties("clinica.clinical.seal")
public record SealProperties(@DefaultValue("clinical-seal") String transitKey, Map<String, String> retiredPublicKeys) {
}
