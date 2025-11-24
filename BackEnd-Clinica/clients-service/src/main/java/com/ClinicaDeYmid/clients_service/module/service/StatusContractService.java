package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundForStatusException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractAlreadyActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractAlreadyInactiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDeletionRestrictedException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusContractService {

    private final ContractRepository contractRepository;

    /**
     * Obtiene el userId del contexto de seguridad actual
     */
    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @Transactional
    @CacheEvict(value = {
            "contract_cache",
            "contract_dto_cache",
            "contracts_by_provider_cache",
            "active_contracts_by_provider_cache",
            "contracts_list_cache",
            "contract_by_number_cache"
    }, allEntries = true)
    public void deleteContract(Long contractId) {
        log.info("Iniciando eliminación lógica de contrato con ID: {}", contractId);

        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new ContractNotFoundForStatusException(contractId, "eliminar"));

            if (hasBusinessRestrictions(contract)) {
                throw new ContractDeletionRestrictedException(contractId,
                        "El contrato tiene restricciones de negocio que impiden su eliminación");
            }

            // Soft delete con auditoría
            Long userId = getCurrentUserId();
            contract.markAsDeleted(
                    userId,
                    "Eliminación solicitada por usuario ID: " + userId
            );

            contractRepository.save(contract);

            log.info("Contrato eliminado lógicamente con ID: {} por usuario: {}", contractId, userId);

        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad al eliminar contrato con ID: {}", contractId, ex);
            throw new ContractDeletionRestrictedException(contractId,
                    "El contrato tiene registros relacionados que impiden su eliminación");
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al eliminar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("eliminar contrato con ID: " + contractId, ex);
        }
    }

    @Transactional
    @CacheEvict(value = {
            "contract_cache",
            "contract_dto_cache",
            "contracts_by_provider_cache",
            "active_contracts_by_provider_cache",
            "contracts_list_cache"
    }, allEntries = true)
    public Contract activateContract(Long contractId) {
        log.info("Iniciando activación de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(contract -> {
                        if (contract.getActive()) {
                            log.warn("Intento de activar contrato ya activo con ID: {}", contractId);
                            throw new ContractAlreadyActiveException(contractId);
                        }

                        contract.setActive(true);

                        Long userId = getCurrentUserId();
                        contract.setUpdatedBy(userId);

                        Contract activatedContract = contractRepository.save(contract);

                        log.info("Contrato activado exitosamente con ID: {} por usuario: {}",
                                contractId, userId);
                        return activatedContract;
                    })
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado para activar con ID: {}", contractId);
                        return new ContractNotFoundForStatusException(contractId, "activar");
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al activar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("activar contrato con ID: " + contractId, ex);
        }
    }

    @Transactional
    @CacheEvict(value = {
            "contract_cache",
            "contract_dto_cache",
            "contracts_by_provider_cache",
            "active_contracts_by_provider_cache",
            "contracts_list_cache"
    }, allEntries = true)
    public Contract deactivateContract(Long contractId) {
        log.info("Iniciando desactivación de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(contract -> {
                        if (!contract.getActive()) {
                            log.warn("Intento de desactivar contrato ya inactivo con ID: {}", contractId);
                            throw new ContractAlreadyInactiveException(contractId);
                        }

                        contract.setActive(false);

                        Long userId = getCurrentUserId();
                        contract.setUpdatedBy(userId);

                        Contract deactivatedContract = contractRepository.save(contract);

                        log.info("Contrato desactivado exitosamente con ID: {} por usuario: {}",
                                contractId, userId);
                        return deactivatedContract;
                    })
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado para desactivar con ID: {}", contractId);
                        return new ContractNotFoundForStatusException(contractId, "desactivar");
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al desactivar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("desactivar contrato con ID: " + contractId, ex);
        }
    }

    /**
     * Restaura un contrato eliminado lógicamente
     */
    @Transactional
    @CacheEvict(value = {
            "contract_cache",
            "contract_dto_cache",
            "contracts_by_provider_cache",
            "active_contracts_by_provider_cache",
            "contracts_list_cache"
    }, allEntries = true)
    public Contract restoreContract(Long contractId) {
        log.info("Iniciando restauración de contrato con ID: {}", contractId);

        try {
            Contract contract = contractRepository.findByIdIncludingDeleted(contractId)
                    .orElseThrow(() -> new ContractNotFoundForStatusException(contractId, "restaurar"));

            if (!contract.isDeleted()) {
                log.warn("Intento de restaurar contrato no eliminado con ID: {}", contractId);
                throw new IllegalStateException("El contrato no está eliminado");
            }

            contract.restore();

            Long userId = getCurrentUserId();
            contract.setUpdatedBy(userId);

            Contract restoredContract = contractRepository.save(contract);

            log.info("Contrato restaurado exitosamente con ID: {} por usuario: {}", contractId, userId);
            return restoredContract;

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al restaurar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("restaurar contrato con ID: " + contractId, ex);
        }
    }

    private boolean hasBusinessRestrictions(Contract contract) {
        // Verificar si el contrato está actualmente vigente
        if (contract.isCurrentlyValid()) {
            log.warn("Intento de eliminar contrato vigente con ID: {}", contract.getId());
            return true;
        }

        return false;
    }
}