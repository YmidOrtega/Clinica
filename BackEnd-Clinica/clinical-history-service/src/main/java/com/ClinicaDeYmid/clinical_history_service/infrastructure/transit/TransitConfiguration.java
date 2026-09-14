package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultOperations;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransitProperties.class)
public class TransitConfiguration {

    @Bean
    TransitClient transitClient(VaultOperations vault, TransitProperties properties) {
        return new TransitClient(vault, properties.mount());
    }
}
