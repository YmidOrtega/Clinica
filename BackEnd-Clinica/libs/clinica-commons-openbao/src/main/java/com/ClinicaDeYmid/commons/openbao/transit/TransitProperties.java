package com.ClinicaDeYmid.commons.openbao.transit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.openbao.transit")
public record TransitProperties(@DefaultValue("transit") String mount, @DefaultValue("5m") Duration keyRefreshInterval) {
}
