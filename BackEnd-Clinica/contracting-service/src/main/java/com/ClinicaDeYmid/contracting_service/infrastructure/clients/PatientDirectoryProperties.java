package com.ClinicaDeYmid.contracting_service.infrastructure.clients;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.contracting.patient-directory")
public record PatientDirectoryProperties(@DefaultValue("24h") Duration ttl, @DefaultValue("50000") long maximumSize) {
}
