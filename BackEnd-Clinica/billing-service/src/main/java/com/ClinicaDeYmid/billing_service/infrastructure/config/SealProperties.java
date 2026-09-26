package com.ClinicaDeYmid.billing_service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties("clinica.billing.seal")
public record SealProperties(@DefaultValue("billing-seal") String transitKey,
                             Map<String, String> retiredPublicKeys) {
}
