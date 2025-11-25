package com.ClinicaDeYmid.patient_service.module.config;

import com.ClinicaDeYmid.patient_service.infra.security.FeignClientInterceptor;
import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Feign Client para el servicio de pacientes.
 * 
 * Configura los interceptores y el nivel de logging para las peticiones Feign.
 */
@Configuration
@RequiredArgsConstructor
public class FeignConfig {

    private final FeignClientInterceptor feignClientInterceptor;

    /**
     * Registra el interceptor que propaga el token JWT a las peticiones Feign.
     *
     * @return RequestInterceptor configurado
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return feignClientInterceptor;
    }

    /**
     * Configura el nivel de logging de Feign.
     * BASIC: registra método, URL, código de respuesta y tiempo de ejecución.
     *
     * @return Logger.Level configurado
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
