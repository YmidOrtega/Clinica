package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundForStatusException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderAlreadyInactiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDeletionRestrictedException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;

    @Transactional
    public void deleteHealthProvider(Long id) {
        log.info("Iniciando eliminación de proveedor de salud con ID: {}", id);

        try {
            // Verificar que el proveedor existe antes de eliminarlo
            HealthProvider provider = healthProviderRepository.findById(id)
                    .orElseThrow(() -> new HealthProviderNotFoundForStatusException(id, "eliminar"));

            // Verificar si tiene contratos activos o restricciones
            if (hasActiveContracts(provider)) {
                throw new HealthProviderDeletionRestrictedException(id,
                        "El proveedor tiene contratos activos asociados");
            }

            healthProviderRepository.deleteById(id);
            log.info("Proveedor de salud eliminado exitosamente con ID: {}", id);

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
                        HealthProvider activatedProvider = healthProviderRepository.save(provider);

                        log.info("Proveedor de salud activado exitosamente con ID: {}", id);
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
                        HealthProvider deactivatedProvider = healthProviderRepository.save(provider);

                        log.info("Proveedor de salud desactivado exitosamente con ID: {}", id);
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

    private boolean hasActiveContracts(HealthProvider provider) {
        // Implementar lógica para verificar contratos activos
        return provider.getContracts() != null &&
                provider.getContracts().stream().anyMatch(contract ->
                        contract.getActive() != null && contract.getActive());
    }
}