package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatusHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;


    @Transactional
    public void deleteHealthProvider(Long id) {
        healthProviderRepository.deleteById(id);
    }

    @Transactional
    public HealthProvider activateHealthProvider(Long id) {
        return healthProviderRepository.findById(id)
                .map(provider -> {
                    provider.setActive(true);
                    return healthProviderRepository.save(provider);
                })
                .orElseThrow(() -> new RuntimeException("Proveedor de salud no encontrado con ID: " + id));
    }

    @Transactional
    public HealthProvider deactivateHealthProvider(Long id) {
        return healthProviderRepository.findById(id)
                .map(provider -> {
                    provider.setActive(false);
                    return healthProviderRepository.save(provider);
                })
                .orElseThrow(() -> new RuntimeException("Proveedor de salud no encontrado con ID: " + id));
    }
}
