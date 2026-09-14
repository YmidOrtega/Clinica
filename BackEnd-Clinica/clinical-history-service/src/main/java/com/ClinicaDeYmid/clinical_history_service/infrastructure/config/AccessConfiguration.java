package com.ClinicaDeYmid.clinical_history_service.infrastructure.config;

import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccessConfiguration.AccessProperties.class)
public class AccessConfiguration {

    @ConfigurationProperties("clinica.clinical.access")
    public record AccessProperties(
            @DefaultValue("30d") Duration relationshipAfterClosure,
            @DefaultValue("4h") Duration emergencyAccessDuration) {
    }

    @Bean
    AccessPolicy accessPolicy(AccessProperties properties) {
        return new AccessPolicy(properties.relationshipAfterClosure(), properties.emergencyAccessDuration());
    }
}
