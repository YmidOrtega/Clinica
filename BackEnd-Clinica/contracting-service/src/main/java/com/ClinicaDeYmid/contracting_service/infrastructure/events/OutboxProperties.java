package com.ClinicaDeYmid.contracting_service.infrastructure.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.contracting.outbox")
public record OutboxProperties(@DefaultValue("7d") Duration retention, @DefaultValue("1000") int purgeBatchSize) {
}
