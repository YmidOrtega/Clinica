package com.ClinicaDeYmid.commons.openbao.transit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultOperations;

@AutoConfiguration(afterName = "org.springframework.cloud.vault.config.VaultAutoConfiguration")
@EnableConfigurationProperties(TransitProperties.class)
public class TransitAutoConfiguration {

    @Bean
    @ConditionalOnBean(VaultOperations.class)
    @ConditionalOnMissingBean
    public TransitClient transitClient(VaultOperations vault, TransitProperties properties) {
        return new TransitClient(vault, properties.mount());
    }
}
