package com.ClinicaDeYmid.clinical_history_service.infrastructure.config;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NotePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClinicalNoteConfiguration.NoteProperties.class)
public class ClinicalNoteConfiguration {

    @ConfigurationProperties("clinica.clinical.notes")
    public record NoteProperties(
            @DefaultValue("24h") Duration extemporaneousAfter,
            @DefaultValue("2m") Duration clockSkewTolerance,
            @DefaultValue("5m") Duration maxAuthenticationAgeToSign) {
    }

    @Bean
    NotePolicy notePolicy(NoteProperties properties) {
        return new NotePolicy(properties.extemporaneousAfter(), properties.clockSkewTolerance(), properties.maxAuthenticationAgeToSign());
    }
}
