package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateHealthProviderNitException;
import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateContractNumberException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderValidationException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeathProviderRecordService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;
    private final ContractRepository contractRepository;

    @Transactional
    public HealthProvider createHealthProvider(CreateHealthProviderDto createDto) {

        log.info("Iniciando creación de proveedor de salud con NIT: {}",
                createDto.nit() != null ? createDto.nit().getValue() : "NULL");

        try {
            HealthProvider healthProvider = healthProviderMapper.toEntity(createDto);

            // Validaciones de negocio
            validateHealthProviderData(healthProvider);
            validateUniqueNit(healthProvider);
            validateUniqueContractNumbers(healthProvider);

            HealthProvider savedProvider = healthProviderRepository.save(healthProvider);

            log.info("Proveedor de salud creado exitosamente con ID: {} y NIT: {}",
                    savedProvider.getId(), savedProvider.getNit().getValue());

            return savedProvider;

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al crear proveedor de salud", ex);
            throw new HealthProviderDataAccessException("crear proveedor de salud", ex);
        }
    }

    private void validateHealthProviderData(HealthProvider healthProvider) {
        if (healthProvider.getNit() == null || healthProvider.getNit().getValue() == null) {
            throw new HealthProviderValidationException("nit", "null", "El NIT es obligatorio");
        }

        if (healthProvider.getSocialReason() == null || healthProvider.getSocialReason().trim().isEmpty()) {
            throw new HealthProviderValidationException("socialReason", healthProvider.getSocialReason(),
                    "La razón social es obligatoria");
        }

        if (healthProvider.getTypeProvider() == null) {
            throw new HealthProviderValidationException("typeProvider", "null",
                    "El tipo de proveedor es obligatorio");
        }
    }

    private void validateUniqueNit(HealthProvider healthProvider) {
        if (healthProvider.getNit() != null && healthProvider.getNit().getValue() != null) {
            healthProviderRepository.findByNit_Value(healthProvider.getNit().getValue())
                    .ifPresent(existingProvider -> {
                        log.warn("Intento de crear proveedor con NIT duplicado: {}",
                                healthProvider.getNit().getValue());
                        throw new DuplicateHealthProviderNitException(healthProvider.getNit().getValue());
                    });
        }
    }

    private void validateUniqueContractNumbers(HealthProvider healthProvider) {
        if (healthProvider.getContracts() != null && !healthProvider.getContracts().isEmpty()) {
            for (Contract contract : healthProvider.getContracts()) {
                if (contract.getContractNumber() != null) {
                    contractRepository.findByContractNumber(contract.getContractNumber())
                            .ifPresent(existingContract -> {
                                log.warn("Intento de crear contrato con número duplicado: {}",
                                        contract.getContractNumber());
                                throw new DuplicateContractNumberException(contract.getContractNumber());
                            });
                }
            }
        }
    }
}