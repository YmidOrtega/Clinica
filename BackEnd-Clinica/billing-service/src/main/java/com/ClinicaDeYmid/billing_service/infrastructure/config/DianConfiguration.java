package com.ClinicaDeYmid.billing_service.infrastructure.config;

import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DianConfiguration {

    @Bean
    DianSoftware dianSoftware(@Value("${clinica.billing.dian.software-id}") String softwareId,
                              @Value("${clinica.billing.dian.software-pin}") String pin) {
        return new DianSoftware(softwareId, pin);
    }
}
