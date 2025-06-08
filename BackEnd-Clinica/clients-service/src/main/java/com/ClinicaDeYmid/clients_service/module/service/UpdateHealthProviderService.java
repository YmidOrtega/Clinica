package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;

    @Transactional
    public HealthProvider updateHealthProvider(Long id, HealthProvider updatedProviderDetails) {
        return healthProviderRepository.findById(id).map(existingProvider -> {

            existingProvider.setSocialReason(updatedProviderDetails.getSocialReason());
            existingProvider.setContract(updatedProviderDetails.getContract());
            existingProvider.setNumberContract(updatedProviderDetails.getNumberContract());
            existingProvider.setTypeProvider(updatedProviderDetails.getTypeProvider());
            existingProvider.setAddress(updatedProviderDetails.getAddress());
            existingProvider.setPhone(updatedProviderDetails.getPhone());
            existingProvider.setActive(updatedProviderDetails.getActive());
            existingProvider.setYearOfValidity(updatedProviderDetails.getYearOfValidity());
            existingProvider.setYearCompletion(updatedProviderDetails.getYearCompletion());

            if (updatedProviderDetails.getNit() != null && !updatedProviderDetails.getNit().equals(existingProvider.getNit())) {
                healthProviderRepository.findByNit_Value(updatedProviderDetails.getNit().getValue())
                        .ifPresent(p -> {
                            // Solo si el NIT existe y no es el mismo proveedor que estamos actualizando
                            if (!p.getId().equals(id)) {
                                throw new IllegalArgumentException("Ya existe un proveedor con el NIT: " + p.getNit().getValue());
                            }
                        });
                existingProvider.setNit(updatedProviderDetails.getNit());
            }

            return healthProviderRepository.save(existingProvider);
        }).orElseThrow(() -> new RuntimeException("Proveedor de salud no encontrado con ID: " + id)); // O una excepción personalizada
    }
}
