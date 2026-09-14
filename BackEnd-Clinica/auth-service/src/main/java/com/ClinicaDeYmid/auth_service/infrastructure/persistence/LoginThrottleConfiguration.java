package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class LoginThrottleConfiguration {

    @Bean
    LoginThrottlePolicy loginThrottlePolicy(LoginThrottleProperties properties) {
        return new LoginThrottlePolicy(properties.addressFreeAttempts(), properties.addressMaxDelay(), properties.accountFreeAttempts(),
                properties.accountMaxDelay(), properties.accountLockThreshold());
    }
}
