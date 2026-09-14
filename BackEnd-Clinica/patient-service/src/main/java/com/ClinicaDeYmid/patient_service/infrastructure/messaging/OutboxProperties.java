package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.patient.outbox")
public record OutboxProperties(
        @DefaultValue("7d") Duration retention,
        @DefaultValue("1000") int purgeBatchSize) {
}
