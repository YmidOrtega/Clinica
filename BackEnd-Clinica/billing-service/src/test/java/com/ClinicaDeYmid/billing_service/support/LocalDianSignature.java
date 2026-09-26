package com.ClinicaDeYmid.billing_service.support;

import com.ClinicaDeYmid.billing_service.infrastructure.dian.DianSigningKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class LocalDianSignature {

    @Bean
    @Primary
    DianSigningKey localDianSigningKey() {
        return LocalDianSigningKey.SHARED;
    }
}
