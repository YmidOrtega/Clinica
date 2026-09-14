package com.ClinicaDeYmid.auth_service.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock(@Value("${clinica.auth.time-zone:America/Bogota}") ZoneId zone) {
        return Clock.system(zone);
    }
}
