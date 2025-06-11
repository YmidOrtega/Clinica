package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.infra.exception.UpdateHealthProviderNitConflictException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderUpdateException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;

    @Transactional
    public HealthProvider updateHealthProvider(String nit, HealthProvider updatedProviderDetails) {

        log.info("Iniciando actualización de proveedor de salud con NIT: {}", nit);

        try {
            return healthProviderRepository.findByNit_Value(nit)
                    .map(existingProvider -> {
                        updateProviderFields(existingProvider, updatedProviderDetails);
                        validateAndUpdateNit(existingProvider, updatedProviderDetails, nit);

                        HealthProvider updatedProvider = healthProviderRepository.save(existingProvider);

                        log.info("Proveedor de salud actualizado exitosamente con ID: {} y NIT: {}",
                                updatedProvider.getId(), updatedProvider.getNit().getValue());

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
        if (updatedDetails.getActive() != null) {
            existingProvider.setActive(updatedDetails.getActive());
        }
        if (updatedDetails.getYearOfValidity() != null) {
            existingProvider.setYearOfValidity(updatedDetails.getYearOfValidity());
        }
        if (updatedDetails.getYearCompletion() != null) {
            existingProvider.setYearCompletion(updatedDetails.getYearCompletion());
        }
    }

    private void validateAndUpdateNit(HealthProvider existingProvider, HealthProvider updatedDetails, String originalNit) {
        if (updatedDetails.getNit() != null &&
                !updatedDetails.getNit().equals(existingProvider.getNit())) {

            String newNitValue = updatedDetails.getNit().getValue();

            healthProviderRepository.findByNit_Value(newNitValue)
                    .ifPresent(conflictingProvider -> {
                        if (!conflictingProvider.getId().equals(existingProvider.getId())) {
                            log.warn("Intento de actualizar con NIT conflictivo: {} ya existe para ID: {}",
                                    newNitValue, conflictingProvider.getId());
                            throw new UpdateHealthProviderNitConflictException(newNitValue,
                                    conflictingProvider.getId().toString());
                        }
                    });

            existingProvider.setNit(updatedDetails.getNit());
            log.info("NIT actualizado de {} a {}", originalNit, newNitValue);
        }
    }
}