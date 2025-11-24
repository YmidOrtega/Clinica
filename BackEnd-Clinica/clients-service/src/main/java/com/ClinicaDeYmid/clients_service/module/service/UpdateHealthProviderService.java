package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.infra.exception.UpdateHealthProviderNitConflictException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderUpdateException;
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
public class UpdateHealthProviderService {

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
    @CacheEvict(value = "health_provider_cache", allEntries = true)
    public HealthProvider updateHealthProvider(String nit, HealthProvider updatedProviderDetails) {

        log.info("Iniciando actualización de proveedor de salud con NIT: {}", nit);

        try {
            return healthProviderRepository.findByNit_Value(nit)
                    .map(existingProvider -> {
                        updateProviderFields(existingProvider, updatedProviderDetails);
                        validateAndUpdateNit(existingProvider, updatedProviderDetails, nit);

                        // Establecer auditoría
                        Long userId = getCurrentUserId();
                        existingProvider.setUpdatedBy(userId);

                        HealthProvider updatedProvider = healthProviderRepository.save(existingProvider);

                        log.info("Proveedor de salud actualizado exitosamente con ID: {} y NIT: {} por usuario: {}",
                                updatedProvider.getId(), updatedProvider.getNit().getValue(), userId);

                        return updatedProvider;
                    })
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado con NIT: {}", nit);
                        return new HealthProviderNotFoundException(nit);
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al actualizar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("actualizar proveedor de salud con NIT: " + nit, ex);
        } catch (HealthProviderNotFoundException | UpdateHealthProviderNitConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al actualizar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderUpdateException(nit, ex.getMessage(), ex);
        }
    }

    private void updateProviderFields(HealthProvider existingProvider, HealthProvider updatedDetails) {
        if (updatedDetails.getSocialReason() != null) {
            existingProvider.setSocialReason(updatedDetails.getSocialReason());
        }
        if (updatedDetails.getTypeProvider() != null) {
            existingProvider.setTypeProvider(updatedDetails.getTypeProvider());
        }
        if (updatedDetails.getAddress() != null) {
            existingProvider.setAddress(updatedDetails.getAddress());
        }
        if (updatedDetails.getPhone() != null) {
            existingProvider.setPhone(updatedDetails.getPhone());
        }
    }

    private void validateAndUpdateNit(HealthProvider existingProvider,
                                      HealthProvider updatedDetails,
                                      String originalNit) {
        if (updatedDetails.getNit() != null &&
                !updatedDetails.getNit().getValue().equals(originalNit)) {

            if (healthProviderRepository.existsByNit_Value(updatedDetails.getNit().getValue())) {
                log.error("Intento de actualizar a NIT duplicado: {}", updatedDetails.getNit().getValue());
                throw new UpdateHealthProviderNitConflictException(
                        originalNit,
                        updatedDetails.getNit().getValue()
                );
            }
            existingProvider.setNit(updatedDetails.getNit());
        }
    }
}