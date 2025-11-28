package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundForStatusException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyInactiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderWithActiveContractsException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Valida que el NIT no sea nulo o vacío
     */
    private void validateNitInput(String nit) {
        if (nit == null || nit.trim().isEmpty()) {
            log.error("NIT nulo o vacío en la solicitud");
            throw new IllegalArgumentException("El NIT no puede ser nulo o vacío");
        }
    }

    @Transactional
    @CacheEvict(value = "health-provider-entities", key = "#nit")
    public HealthProvider activateHealthProvider(String nit) {
        validateNitInput(nit);

        log.info("Iniciando activación de proveedor de salud con NIT: {}", nit);
        log.debug("🗑️ Invalidando cache para health provider: {}", nit);

        try {
            HealthProvider provider = healthProviderRepository.findByNit_Value(nit)
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para activar con NIT: {}", nit);
                        return new HealthProviderNotFoundForStatusException(nit, "activar");
                    });

            // Validar que no esté ya activo
            if (provider.getActive()) {
                log.warn("Intento de activar proveedor ya activo con NIT: {}", nit);
                throw new HealthProviderAlreadyActiveException(nit, provider.getSocialReason());
            }

            // Activar proveedor
            provider.setActive(true);

            Long userId = getCurrentUserId();
            provider.setUpdatedBy(userId);

            HealthProvider activatedProvider = healthProviderRepository.save(provider);

            log.info("Proveedor de salud activado exitosamente con NIT: {} por usuario: {}", nit, userId);
            return activatedProvider;

        } catch (HealthProviderNotFoundForStatusException | HealthProviderAlreadyActiveException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al activar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("activar proveedor de salud con NIT: " + nit, ex);
        }
    }

    @Transactional
    @CacheEvict(value = "health-provider-entities", key = "#nit")
    public HealthProvider deactivateHealthProvider(String nit) {
        validateNitInput(nit);

        log.info("Iniciando desactivación de proveedor de salud con NIT: {}", nit);
        log.debug("🗑️ Invalidando cache para health provider: {}", nit);

        try {
            HealthProvider provider = healthProviderRepository.findByNit_Value(nit)
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para desactivar con NIT: {}", nit);
                        return new HealthProviderNotFoundForStatusException(nit, "desactivar");
                    });

            // Validar que no esté ya inactivo
            if (!provider.getActive()) {
                log.warn("Intento de desactivar proveedor ya inactivo con NIT: {}", nit);
                throw new HealthProviderAlreadyInactiveException(nit, provider.getSocialReason());
            }

            // Validar que no tenga contratos activos
            if (hasActiveContracts(provider)) {
                log.warn("Intento de desactivar proveedor con contratos activos - NIT: {}", nit);
                throw new HealthProviderWithActiveContractsException(nit, provider.getSocialReason());
            }

            // Desactivar proveedor
            provider.setActive(false);

            Long userId = getCurrentUserId();
            provider.setUpdatedBy(userId);

            HealthProvider deactivatedProvider = healthProviderRepository.save(provider);

            log.info("Proveedor de salud desactivado exitosamente con NIT: {} por usuario: {}", nit, userId);
            return deactivatedProvider;

        } catch (HealthProviderNotFoundForStatusException |
                 HealthProviderAlreadyInactiveException |
                 HealthProviderWithActiveContractsException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al desactivar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("desactivar proveedor de salud con NIT: " + nit, ex);
        }
    }

    @Transactional
    @CacheEvict(value = "health-provider-entities", key = "#nit")
    public HealthProvider softDeleteHealthProvider(String nit, String reason) {
        validateNitInput(nit);

        log.info("Iniciando eliminación lógica de proveedor de salud con NIT: {} - Razón: {}", nit, reason);
        log.debug("🗑️ Invalidando cache para health provider: {}", nit);

        try {
            HealthProvider provider = healthProviderRepository.findByNit_Value(nit)
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para eliminar con NIT: {}", nit);
                        return new HealthProviderNotFoundForStatusException(nit, "eliminar");
                    });

            // Validar que no tenga contratos activos
            if (hasActiveContracts(provider)) {
                log.warn("Intento de eliminar proveedor con contratos activos - NIT: {}", nit);
                throw new HealthProviderWithActiveContractsException(nit, provider.getSocialReason());
            }

            // Soft delete con auditoría
            Long userId = getCurrentUserId();
            provider.markAsDeleted(userId, reason != null ? reason : "No reason provided");

            HealthProvider deletedProvider = healthProviderRepository.save(provider);

            log.info("Proveedor de salud eliminado lógicamente con NIT: {} por usuario: {}", nit, userId);
            return deletedProvider;

        } catch (HealthProviderNotFoundForStatusException | HealthProviderWithActiveContractsException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al eliminar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("eliminar proveedor de salud con NIT: " + nit, ex);
        }
    }

    @Transactional
    @CacheEvict(value = "health-provider-entities", key = "#nit")
    public HealthProvider restoreHealthProvider(String nit) {
        validateNitInput(nit);

        log.info("Iniciando restauración de proveedor de salud con NIT: {}", nit);
        log.debug("🗑️ Invalidando cache para health provider: {}", nit);

        try {
            // ⚠️ IMPORTANTE: Usar findByNitIncludingDeleted porque el proveedor está eliminado
            HealthProvider provider = healthProviderRepository.findByNitIncludingDeleted(nit)
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado para restaurar con NIT: {}", nit);
                        return new HealthProviderNotFoundForStatusException(nit, "restaurar");
                    });

            // Validar que esté eliminado
            if (!provider.isDeleted()) {
                log.warn("Intento de restaurar proveedor no eliminado con NIT: {}", nit);
                throw new IllegalStateException("El proveedor con NIT " + nit + " no está eliminado");
            }

            // Restaurar proveedor
            provider.restore();

            Long userId = getCurrentUserId();
            provider.setUpdatedBy(userId);

            HealthProvider restoredProvider = healthProviderRepository.save(provider);

            log.info("Proveedor de salud restaurado exitosamente con NIT: {} por usuario: {}", nit, userId);
            return restoredProvider;

        } catch (HealthProviderNotFoundForStatusException | IllegalStateException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al restaurar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("restaurar proveedor de salud con NIT: " + nit, ex);
        }
    }

    private boolean hasActiveContracts(HealthProvider provider) {
        return provider.getContracts() != null &&
                provider.getContracts().stream()
                        .anyMatch(contract -> contract.getActive() != null && contract.getActive());
    }
}