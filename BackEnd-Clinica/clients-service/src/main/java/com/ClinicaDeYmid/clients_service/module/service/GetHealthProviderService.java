package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.GetHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Transactional(readOnly = true)
    public GetHealthProviderDto getHealthProviderByNit(String nit) {
        log.info("Consultando proveedor de salud con NIT: {}", nit);

        try {
            HealthProvider healthProvider = healthProviderRepository.findByNit_Value(nit)
                    .orElseThrow(() -> {
                        log.error("Proveedor de salud no encontrado con NIT: {}", nit);
                        return new HealthProviderNotFoundException(nit);
                    });

            if (!healthProvider.getActive()) {
                log.warn("Intento de consultar proveedor inactivo con NIT: {}", nit);
                throw new HealthProviderNotActiveException(healthProvider.getSocialReason(), nit);
            }

            return healthProviderMapper.toResponseDto(healthProvider);

        } catch (DataAccessException ex) {
            throw new HealthProviderDataAccessException("obtener información del proveedor con NIT: " + nit, ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> getAllHealthProviders(Pageable pageable) {
        try {
            Page<HealthProvider> healthProvidersPage = healthProviderRepository.findAllByActiveTrue(pageable);

            List<HealthProviderListDto> filteredAndMappedContent = healthProvidersPage.getContent().stream()
                    .filter(HealthProvider::getActive)
                    .map(healthProviderMapper::toHealthProviderListDto)
                    .toList();

            return new PageImpl<>(filteredAndMappedContent, pageable, healthProvidersPage.getTotalElements());

        } catch (DataAccessException ex) {
            throw new HealthProviderDataAccessException("obtener la lista paginada de proveedores de salud", ex);
        }
    }
}