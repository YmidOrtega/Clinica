package com.ClinicaDeYmid.admissions_service.infrastructure.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties("clinica.admissions.seal")
public record SealProperties(@DefaultValue("admissions-seal") String transitKey,
                             Map<String, String> retiredPublicKeys) {
}
