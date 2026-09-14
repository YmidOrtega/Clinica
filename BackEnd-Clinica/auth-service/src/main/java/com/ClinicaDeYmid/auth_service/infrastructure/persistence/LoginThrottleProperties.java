package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("clinica.auth.login-throttle")
public record LoginThrottleProperties(@DefaultValue("5") int addressFreeAttempts, @DefaultValue("15m") Duration addressMaxDelay,
                                      @DefaultValue("10") int accountFreeAttempts, @DefaultValue("1m") Duration accountMaxDelay,
                                      @DefaultValue("100") int accountLockThreshold, @DefaultValue("30d") Duration retention,
                                      @DefaultValue("1000") int purgeBatchSize) {
}
