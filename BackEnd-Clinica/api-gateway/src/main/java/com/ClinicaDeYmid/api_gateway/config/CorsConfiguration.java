package com.ClinicaDeYmid.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Configuración CORS siguiendo buenas prácticas de seguridad
 * - Orígenes específicos (no wildcard en producción)
 * - Métodos HTTP limitados
 * - Headers controlados
 * - Credentials permitidos de forma segura
 */
@Configuration
public class CorsConfiguration {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4321}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String[] allowedMethods;

    @Value("${cors.max-age:3600}")
    private Long maxAge;

    @Bean
    public CorsWebFilter corsWebFilter() {
        org.springframework.web.cors.CorsConfiguration corsConfig = 
                new org.springframework.web.cors.CorsConfiguration();

        // Orígenes permitidos - NUNCA usar "*" con credentials
        corsConfig.setAllowedOrigins(Arrays.asList(allowedOrigins));
        
        // Métodos HTTP permitidos
        corsConfig.setAllowedMethods(Arrays.asList(allowedMethods));

        // Headers permitidos
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // Headers expuestos al cliente
        corsConfig.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count",
                "X-RateLimit-Remaining",
                "Retry-After",
                "Content-Disposition"
        ));

        // Permitir envío de credenciales (cookies, auth headers)
        corsConfig.setAllowCredentials(true);

        // Tiempo de caché de la respuesta preflight (OPTIONS)
        corsConfig.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
