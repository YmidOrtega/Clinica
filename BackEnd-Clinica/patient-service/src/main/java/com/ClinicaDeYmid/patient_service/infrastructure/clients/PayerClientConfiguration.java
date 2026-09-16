package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import com.ClinicaDeYmid.commons.security.DelegatedTokens;
import feign.RequestInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

public class PayerClientConfiguration {

    @Bean
    RequestInterceptor payerServiceToken(ObjectProvider<DelegatedTokens> tokens) {
        return template -> {
            DelegatedTokens delegated = tokens.getIfAvailable();
            if (delegated != null && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + delegated.serviceToken());
            }
        };
    }
}
