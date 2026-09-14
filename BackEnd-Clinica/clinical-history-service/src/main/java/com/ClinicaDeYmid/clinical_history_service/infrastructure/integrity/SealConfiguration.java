package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class SealConfiguration {

    @Bean
    EcdsaClinicalSignature clinicalSignature(SealProperties properties) {
        return new EcdsaClinicalSignature(SealKeyRing.load(properties));
    }
}
