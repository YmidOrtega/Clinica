package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
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
import org.springframework.cache.annotation.CacheEvict;
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

    /**
     * Obtiene el userId del contexto de seguridad actual
     */
    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L; // Fallback para desarrollo/testing
        }
        return userId;
    }

    @Transactional
    @CacheEvict(value = "health_provider_cache", allEntries = true)
    public HealthProvider createHealthProvider(CreateHealthProviderDto createDto) {

        log.info("Iniciando creación de proveedor de salud con NIT: {}",
                createDto.nit() != null ? createDto.nit().getValue() : "NULL");

        try {
            HealthProvider healthProvider = healthProviderMapper.toEntity(createDto);

            // Validaciones de negocio
            validateHealthProviderData(healthProvider);
            validateUniqueNit(healthProvider);
            validateUniqueContractNumbers(healthProvider);

            // Establecer auditoría
            Long userId = getCurrentUserId();
            healthProvider.setCreatedBy(userId);
            healthProvider.setUpdatedBy(userId);

            // Establecer auditoría en contratos
            if (healthProvider.getContracts() != null) {
                healthProvider.getContracts().forEach(contract -> {
                    contract.setCreatedBy(userId);
                    contract.setUpdatedBy(userId);
                });
            }

            HealthProvider savedProvider = healthProviderRepository.save(healthProvider);

            log.info("Proveedor de salud creado exitosamente con ID: {} y NIT: {} por usuario: {}",
                    savedProvider.getId(), savedProvider.getNit().getValue(), userId);

            return savedProvider;

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al crear proveedor de salud", ex);
            throw new HealthProviderDataAccessException("crear proveedor de salud", ex);
        }
    }

    private void validateHealthProviderData(HealthProvider healthProvider) {
        if (healthProvider.getNit() == null || healthProvider.getNit().getValue() == null) {
            log.error("Intento de crear proveedor sin NIT");
            throw new HealthProviderValidationException("El NIT es obligatorio");
        }

        if (healthProvider.getSocialReason() == null || healthProvider.getSocialReason().isBlank()) {
            log.error("Intento de crear proveedor sin razón social");
            throw new HealthProviderValidationException("La razón social es obligatoria");
        }

        if (healthProvider.getTypeProvider() == null) {
            log.error("Intento de crear proveedor sin tipo");
            throw new HealthProviderValidationException("El tipo de proveedor es obligatorio");
        }
    }

    private void validateUniqueNit(HealthProvider healthProvider) {
        String nitValue = healthProvider.getNit().getValue();

        if (healthProviderRepository.existsByNit_Value(nitValue)) {
            log.error("Intento de crear proveedor con NIT duplicado: {}", nitValue);
            throw new DuplicateHealthProviderNitException(nitValue);
        }
    }

    private void validateUniqueContractNumbers(HealthProvider healthProvider) {
        if (healthProvider.getContracts() != null && !healthProvider.getContracts().isEmpty()) {
            for (Contract contract : healthProvider.getContracts()) {
                if (contract.getContractNumber() != null &&
                        contractRepository.existsByContractNumber(contract.getContractNumber())) {
                    log.error("Intento de crear contrato con número duplicado: {}", contract.getContractNumber());
                    throw new DuplicateContractNumberException(contract.getContractNumber());
                }
            }
        }
    }
}