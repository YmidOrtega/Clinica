package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundForStatusException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyInactiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDeletionRestrictedException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;

    /**
     * Obtiene el userId del contexto de seguridad actual
     */
    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @Transactional
    @CacheEvict(value = {"health_provider_cache", "health_providers_list_cache"}, allEntries = true)
    public void deleteHealthProvider(Long id) {
        log.info("Iniciando eliminación lógica de proveedor de salud con ID: {}", id);

        try {
            HealthProvider provider = healthProviderRepository.findById(id)
                    .orElseThrow(() -> new HealthProviderNotFoundForStatusException(id, "eliminar"));

            if (hasActiveContracts(provider)) {
                throw new HealthProviderDeletionRestrictedException(id,
                        "El proveedor tiene contratos activos asociados");
            }

            // Soft delete con auditoría
            Long userId = getCurrentUserId();
            provider.markAsDeleted(
                    userId,
                    "Eliminación solicitada por usuario ID: " + userId
            );

            healthProviderRepository.save(provider);

            log.info("Proveedor de salud eliminado lógicamente con ID: {} por usuario: {}", id, userId);

        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad al eliminar proveedor con ID: {}", id, ex);
            throw new HealthProviderDeletionRestrictedException(id,
                    "El proveedor tiene registros relacionados que impiden su eliminación");
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al eliminar proveedor con ID: {}", id, ex);
            throw new HealthProviderDataAccessException("eliminar proveedor de salud con ID: " + id, ex);
        }
    }

    @Transactional
    @CacheEvict(value = {"health_provider_cache", "health_providers_list_cache"}, allEntries = true)
    public HealthProvider activateHealthProvider(Long id) {
        log.info("Iniciando activación de proveedor de salud con ID: {}", id);

        try {
            return healthProviderRepository.findById(id)
                    .map(provider -> {
                        if (provider.getActive()) {
                            log.warn("Intento de activar proveedor ya activo con ID: {}", id);
                            throw new HealthProviderAlreadyActiveException(id, provider.getSocialReason());
                        }

                        provider.setActive(true);

                        Long userId = getCurrentUserId();
                        provider.setUpdatedBy(userId);

                        HealthProvider activatedProvider = healthProviderRepository.save(provider);

                        log.info("Proveedor de salud activado exitosamente con ID: {} por usuario: {}", id, userId);
                        return activatedProvider;
                    })
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para activar con ID: {}", id);
                        return new HealthProviderNotFoundForStatusException(id, "activar");
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al activar proveedor con ID: {}", id, ex);
            throw new HealthProviderDataAccessException("activar proveedor de salud con ID: " + id, ex);
        }
    }

    @Transactional
    @CacheEvict(value = {"health_provider_cache", "health_providers_list_cache"}, allEntries = true)
    public HealthProvider deactivateHealthProvider(Long id) {
        log.info("Iniciando desactivación de proveedor de salud con ID: {}", id);

        try {
            return healthProviderRepository.findById(id)
                    .map(provider -> {
                        if (!provider.getActive()) {
                            log.warn("Intento de desactivar proveedor ya inactivo con ID: {}", id);
                            throw new HealthProviderAlreadyInactiveException(id, provider.getSocialReason());
                        }

                        provider.setActive(false);

                        Long userId = getCurrentUserId();
                        provider.setUpdatedBy(userId);

                        HealthProvider deactivatedProvider = healthProviderRepository.save(provider);

                        log.info("Proveedor de salud desactivado exitosamente con ID: {} por usuario: {}", id, userId);
                        return deactivatedProvider;
                    })
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para desactivar con ID: {}", id);
                        return new HealthProviderNotFoundForStatusException(id, "desactivar");
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al desactivar proveedor con ID: {}", id, ex);
            throw new HealthProviderDataAccessException("desactivar proveedor de salud con ID: " + id, ex);
        }
    }

    /**
     * Restaura un proveedor eliminado lógicamente
     */
    @Transactional
    @CacheEvict(value = {"health_provider_cache", "health_providers_list_cache"}, allEntries = true)
    public HealthProvider restoreHealthProvider(Long id) {
        log.info("Iniciando restauración de proveedor de salud con ID: {}", id);

        try {
            HealthProvider provider = healthProviderRepository.findByIdIncludingDeleted(id)
                    .orElseThrow(() -> new HealthProviderNotFoundForStatusException(id, "restaurar"));

            if (!provider.isDeleted()) {
                log.warn("Intento de restaurar proveedor no eliminado con ID: {}", id);
                throw new IllegalStateException("El proveedor no está eliminado");
            }

            provider.restore();

            Long userId = getCurrentUserId();
            provider.setUpdatedBy(userId);

            HealthProvider restoredProvider = healthProviderRepository.save(provider);

            log.info("Proveedor de salud restaurado exitosamente con ID: {} por usuario: {}", id, userId);
            return restoredProvider;

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al restaurar proveedor con ID: {}", id, ex);
            throw new HealthProviderDataAccessException("restaurar proveedor de salud con ID: " + id, ex);
        }
    }

    private boolean hasActiveContracts(HealthProvider provider) {
        return provider.getContracts() != null &&
                provider.getContracts().stream()
                        .anyMatch(contract -> contract.getActive() != null && contract.getActive());
    }
}