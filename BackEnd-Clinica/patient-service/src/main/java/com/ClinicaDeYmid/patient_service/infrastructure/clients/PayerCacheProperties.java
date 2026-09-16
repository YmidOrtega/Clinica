package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.patient.payers")
public record PayerCacheProperties(@DefaultValue("5m") Duration freshTtl, @DefaultValue("24h") Duration lastKnownTtl,
                                   @DefaultValue("1000") long maximumSize) {
}
