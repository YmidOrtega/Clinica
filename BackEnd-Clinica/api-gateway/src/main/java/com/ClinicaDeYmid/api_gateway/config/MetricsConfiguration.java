package com.ClinicaDeYmid.api_gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuración de métricas y observabilidad
 * - Habilita métricas de Micrometer
 * - Configura etiquetas comunes
 * - Habilita procesamiento asíncrono
 */
@Configuration
@EnableAsync
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "application", "api-gateway",
                        "environment", System.getProperty("spring.profiles.active", "dev")
                );
    }
}
