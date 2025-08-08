package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.dto.GetHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetHealthProviderContractService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Transactional(readOnly = true)
    public GetHealthProviderDto getProviderWithContract(String nit, Contract contract) {
        HealthProvider provider = healthProviderRepository.findByNit_Value(nit)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con NIT: " + nit));
        return healthProviderMapper.toGetHealthProviderDto(provider, contract);
    }
}
