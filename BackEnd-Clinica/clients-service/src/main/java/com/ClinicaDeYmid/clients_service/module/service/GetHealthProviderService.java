package com.ClinicaDeYmid.clients_service.module.service;


import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;

    @Transactional(readOnly = true)
    public Optional<HealthProvider> getHealthProviderById(Long id) {
        return healthProviderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<HealthProvider> getAllHealthProviders() {
        return healthProviderRepository.findAll();
    }
}
