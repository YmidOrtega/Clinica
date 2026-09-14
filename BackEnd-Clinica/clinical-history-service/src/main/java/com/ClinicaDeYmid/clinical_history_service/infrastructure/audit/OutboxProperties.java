package com.ClinicaDeYmid.clinical_history_service.infrastructure.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.clinical.outbox")
public record OutboxProperties(
        @DefaultValue("7d") Duration retention,
        @DefaultValue("1000") int purgeBatchSize) {
}
