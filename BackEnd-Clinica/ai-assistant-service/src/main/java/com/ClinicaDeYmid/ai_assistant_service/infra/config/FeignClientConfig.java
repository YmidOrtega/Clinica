package com.ClinicaDeYmid.ai_assistant_service.infra.config;

import com.ClinicaDeYmid.ai_assistant_service.module.feignclient.FeignClientInterceptor;
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
