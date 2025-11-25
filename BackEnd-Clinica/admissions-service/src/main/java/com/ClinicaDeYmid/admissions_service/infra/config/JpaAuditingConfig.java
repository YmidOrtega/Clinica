package com.ClinicaDeYmid.admissions_service.infra.config;

import com.ClinicaDeYmid.admissions_service.infra.security.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Configuración de JPA Auditing para captura automática de created_by y updated_by
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@Slf4j
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return new AuditorAwareImpl();
    }

    /**
     * Implementación de AuditorAware que obtiene el userId del UserContextHolder
     */
    public static class AuditorAwareImpl implements AuditorAware<Long> {

        @Override
        public Optional<Long> getCurrentAuditor() {
            Long userId = UserContextHolder.getCurrentUserId();

            if (userId == null) {
                log.warn("No user context available for auditing. Using system user (0).");
                return Optional.of(0L); // Usuario del sistema por defecto
            }

            log.debug("Current auditor userId: {}", userId);
            return Optional.of(userId);
        }
    }
}