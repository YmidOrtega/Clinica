package com.ClinicaDeYmid.ai_assistant_service.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "com.ClinicaDeYmid.ai_assistant_service.client")
public class FeignConfiguration {
}
