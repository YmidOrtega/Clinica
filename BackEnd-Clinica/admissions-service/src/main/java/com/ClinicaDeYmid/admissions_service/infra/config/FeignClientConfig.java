package com.ClinicaDeYmid.admissions_service.infra.config;

import com.ClinicaDeYmid.admissions_service.module.feignclient.FeignClientInterceptor;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FeignClientConfig {

    private final FeignClientInterceptor feignClientInterceptor;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return feignClientInterceptor;
    }
}