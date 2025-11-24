package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.ContractDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetContractService {

    private final ContractRepository contractRepository;
    private final HealthProviderMapper healthProviderMapper;
    private final HealthProviderRepository healthProviderRepository;

    /**
     * Obtener un contrato por ID (para uso interno - retorna entidad)
     */
    @Transactional(readOnly = true)
    public Contract getEntityContractById(Long contractId) {
        log.info("Consultando contrato con ID: {}", contractId);

        try {
            return contractRepository.findByIdWithProvider(contractId)
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado con ID: {}", contractId);
                        return new ContractNotFoundException(contractId);
                    });

        } catch (ContractNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al consultar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("consultar contrato con ID: " + contractId, ex);
        }
    }

    /**
     * Obtener un contrato por ID (retorna DTO)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "contract_dto_cache", key = "#contractId", unless = "#result == null")
    public ContractDto getContractById(Long contractId) {
        log.info("Consultando contrato DTO con ID: {}", contractId);

        try {
            Contract contract = getEntityContractById(contractId);

            if (!contract.getActive()) {
                log.warn("Contrato inactivo con ID: {}", contractId);
            }

            return healthProviderMapper.toContractDto(contract);

        } catch (DataAccessException ex) {
            log.error("Error al convertir contrato a DTO con ID: {}", contractId, ex);
            throw new ContractDataAccessException("obtener contrato DTO con ID: " + contractId, ex);
        }
    }

    /**
     * Obtener contratos por proveedor de salud
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "contracts_by_provider_cache", key = "#healthProviderId")
    public List<ContractDto> getContractsByHealthProvider(Long healthProviderId) {
        log.info("Consultando contratos del proveedor con ID: {}", healthProviderId);

        try {
            List<Contract> contracts = contractRepository.findByHealthProviderId(healthProviderId);

            log.info("Se encontraron {} contratos para el proveedor ID: {}",
                    contracts.size(), healthProviderId);

            return contracts.stream()
                    .map(healthProviderMapper::toContractDto)
                    .collect(Collectors.toList());

        } catch (DataAccessException ex) {
            log.error("Error al consultar contratos del proveedor ID: {}", healthProviderId, ex);
            throw new ContractDataAccessException(
                    "consultar contratos del proveedor ID: " + healthProviderId, ex);
        }
    }

    /**
     * Obtener contratos activos por proveedor de salud
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "active_contracts_by_provider_cache", key = "#healthProviderId")
    public List<ContractDto> getActiveContractsByHealthProvider(Long healthProviderId) {
        log.info("Consultando contratos activos del proveedor con ID: {}", healthProviderId);

        try {
            List<Contract> contracts = contractRepository.findActiveByHealthProviderId(healthProviderId);

            log.info("Se encontraron {} contratos activos para el proveedor ID: {}",
                    contracts.size(), healthProviderId);

            return contracts.stream()
                    .map(healthProviderMapper::toContractDto)
                    .collect(Collectors.toList());

        } catch (DataAccessException ex) {
            log.error("Error al consultar contratos activos del proveedor ID: {}", healthProviderId, ex);
            throw new ContractDataAccessException(
                    "consultar contratos activos del proveedor ID: " + healthProviderId, ex);
        }
    }

    /**
     * Obtener todos los contratos con paginación
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "contracts_list_cache", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ContractDto> getAllContracts(Pageable pageable) {
        log.info("Consultando todos los contratos - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<Contract> contractsPage = contractRepository.findAllActive(pageable);

            log.info("Se encontraron {} contratos activos", contractsPage.getTotalElements());

            return contractsPage.map(healthProviderMapper::toContractDto);

        } catch (DataAccessException ex) {
            log.error("Error al consultar todos los contratos", ex);
            throw new ContractDataAccessException("consultar todos los contratos", ex);
        }
    }

    /**
     * Buscar contrato por número de contrato
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "contract_by_number_cache", key = "#contractNumber", unless = "#result == null")
    public ContractDto getContractByNumber(String contractNumber) {
        log.info("Consultando contrato con número: {}", contractNumber);

        try {
            Contract contract = contractRepository.findByContractNumber(contractNumber)
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado con número: {}", contractNumber);
                        return new ContractNotFoundException(contractNumber);
                    });

            return healthProviderMapper.toContractDto(contract);

        } catch (ContractNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error al consultar contrato por número: {}", contractNumber, ex);
            throw new ContractDataAccessException("consultar contrato por número: " + contractNumber, ex);
        }
    }

    /**
     * Verificar si un contrato existe por número
     */
    @Transactional(readOnly = true)
    public boolean existsByContractNumber(String contractNumber) {
        log.debug("Verificando existencia de contrato con número: {}", contractNumber);

        try {
            return contractRepository.existsByContractNumber(contractNumber);
        } catch (DataAccessException ex) {
            log.error("Error al verificar existencia de contrato: {}", contractNumber, ex);
            throw new ContractDataAccessException(
                    "verificar existencia de contrato: " + contractNumber, ex);
        }
    }

    /**
     * Obtiene contratos eliminados de un proveedor
     */
    @Transactional(readOnly = true)
    public Page<ContractDto> getDeletedContractsByProvider(String nit, Pageable pageable) {
        log.info("Consultando contratos eliminados del proveedor con NIT: {}", nit);

        try {
            // Primero verificar que el proveedor existe
            HealthProvider provider = healthProviderRepository.findByNitIncludingDeleted(nit)
                    .orElseThrow(() -> new HealthProviderNotFoundException(nit));

            Page<Contract> deletedContracts = contractRepository
                    .findDeletedByHealthProviderId(provider.getId(), pageable);

            log.info("Se encontraron {} contratos eliminados para el proveedor NIT: {}",
                    deletedContracts.getTotalElements(), nit);

            return deletedContracts.map(healthProviderMapper::toContractDto);

        } catch (DataAccessException ex) {
            log.error("Error al consultar contratos eliminados del proveedor NIT: {}", nit, ex);
            throw new ContractDataAccessException(
                    "consultar contratos eliminados del proveedor NIT: " + nit, ex);
        }
    }

    /**
     * Obtiene contratos próximos a vencer
     */
    @Transactional(readOnly = true)
    public List<ContractDto> getExpiringContracts(int daysAhead) {
        log.info("Consultando contratos que vencen en los próximos {} días", daysAhead);

        try {
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = startDate.plusDays(daysAhead);

            List<Contract> expiringContracts = contractRepository
                    .findExpiringBetween(startDate, endDate);

            log.info("Se encontraron {} contratos próximos a vencer", expiringContracts.size());

            return expiringContracts.stream()
                    .map(healthProviderMapper::toContractDto)
                    .collect(Collectors.toList());

        } catch (DataAccessException ex) {
            log.error("Error al consultar contratos próximos a vencer", ex);
            throw new ContractDataAccessException("consultar contratos próximos a vencer", ex);
        }
    }

    /**
     * Busca contratos por nombre
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "contracts_search_cache", key = "#searchTerm + '-' + #pageable.pageNumber")
    public Page<ContractDto> searchContractsByName(String searchTerm, Pageable pageable) {
        log.info("Buscando contratos por nombre: {}", searchTerm);

        try {
            Page<Contract> searchResults = contractRepository.searchByName(searchTerm, pageable);

            log.info("Se encontraron {} resultados para '{}'", searchResults.getTotalElements(), searchTerm);

            return searchResults.map(healthProviderMapper::toContractDto);

        } catch (DataAccessException ex) {
            log.error("Error al buscar contratos por nombre: {}", searchTerm, ex);
            throw new ContractDataAccessException("buscar contratos por nombre", ex);
        }
    }
}