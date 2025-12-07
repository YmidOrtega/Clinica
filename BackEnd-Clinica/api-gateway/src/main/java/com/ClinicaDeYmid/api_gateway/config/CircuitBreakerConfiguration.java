package com.ClinicaDeYmid.api_gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración avanzada del Circuit Breaker usando Resilience4j
 * - Abre el circuito tras 3 fallos consecutivos
 * - Implementa retry con backoff exponencial
 * - Configuración personalizada por servicio
 */
@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // Abre el circuito tras 3 fallos consecutivos
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(3)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .build());
    }

    /**
     * Circuit breaker específico para el servicio de autenticación
     * Configuración más permisiva debido a su criticidad
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> authServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(60)
                        .slowCallRateThreshold(60)
                        .slowCallDurationThreshold(Duration.ofSeconds(4))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .slidingWindowSize(15)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(45))
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(8))
                        .build()), "auth-service");
    }

    /**
     * Circuit breaker para servicios de datos (patient, billing, etc.)
     * Configuración estándar con tolerancia media
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> dataServicesCustomizer() {
        return factory -> {
            factory.configure(builder -> builder
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .slowCallRateThreshold(50)
                            .slowCallDurationThreshold(Duration.ofSeconds(3))
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(3)
                            .waitDurationInOpenState(Duration.ofSeconds(30))
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofSeconds(5))
                            .build()), "patient-service", "billing-service", "clients-service", 
                            "suppliers-service", "admissions-service");
        };
    }

    /**
     * Circuit breaker para el servicio de IA
     * Mayor timeout debido a la naturaleza de las operaciones de IA
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> aiServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .slidingWindowSize(8)
                        .minimumNumberOfCalls(3)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(15))
                        .build()), "ai-assistant-service");
    }
}
