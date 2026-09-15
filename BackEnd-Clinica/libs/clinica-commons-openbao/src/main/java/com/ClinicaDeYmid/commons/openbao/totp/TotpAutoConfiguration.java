package com.ClinicaDeYmid.commons.openbao.totp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultOperations;

@AutoConfiguration(afterName = "org.springframework.cloud.vault.config.VaultAutoConfiguration")
@EnableConfigurationProperties(TotpProperties.class)
public class TotpAutoConfiguration {

    @Bean
    @ConditionalOnBean(VaultOperations.class)
    @ConditionalOnMissingBean
    public TotpClient totpClient(VaultOperations vault, TotpProperties properties) {
        return new TotpClient(vault, properties.mount());
    }
}
