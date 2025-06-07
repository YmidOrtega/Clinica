package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.suppliers_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.suppliers_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HeathProviderRegistrationService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Transactional
    public HealthProvider createHealthProvider(CreateHealthProviderDto createDto) {

        HealthProvider healthProvider = healthProviderMapper.toEntity(createDto);

        // 1. Validar que el NIT no exista
        if (healthProvider.getNit() != null) {
            healthProviderRepository.findByNit_Value(healthProvider.getNit().getValue())
                    .ifPresent(p -> {
                        throw new IllegalArgumentException("Ya existe un proveedor con el NIT: " + p.getNit().getValue());
                    });
        }

        // 2. Validar que el número de contrato no exista (si aplica y debe ser único globalmente)
        if (healthProvider.getNumberContract() != null && !healthProvider.getNumberContract().isEmpty()) {
            healthProviderRepository.findByNumberContract(healthProvider.getNumberContract())
                    .ifPresent(p -> {
                        throw new IllegalArgumentException("Ya existe un proveedor con el número de contrato: " + p.getNumberContract());
                    });
        }

        return healthProviderRepository.save(healthProvider);
    }
}
