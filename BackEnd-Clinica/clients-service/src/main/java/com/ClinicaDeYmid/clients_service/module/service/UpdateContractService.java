package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractValidationException;
import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateContractNumberException;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateContractService {

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
    public Contract updateContract(Long contractId, Contract updatedContractDetails) {
        log.info("Iniciando actualización de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(existingContract -> {
                        // Validar fechas si se están actualizando
                        if (updatedContractDetails.getStartDate() != null ||
                                updatedContractDetails.getEndDate() != null) {
                            validateContractDates(
                                    updatedContractDetails.getStartDate() != null ?
                                            updatedContractDetails.getStartDate() : existingContract.getStartDate(),
                                    updatedContractDetails.getEndDate() != null ?
                                            updatedContractDetails.getEndDate() : existingContract.getEndDate()
                            );
                        }

                        // Validar número de contrato si se está cambiando
                        if (updatedContractDetails.getContractNumber() != null &&
                                !updatedContractDetails.getContractNumber().equals(existingContract.getContractNumber())) {
                            validateUniqueContractNumber(updatedContractDetails.getContractNumber());
                        }

                        // Actualizar campos
                        updateContractFields(existingContract, updatedContractDetails);

                        // Establecer auditoría
                        Long userId = getCurrentUserId();
                        existingContract.setUpdatedBy(userId);

                        Contract updatedContract = contractRepository.save(existingContract);

                        log.info("Contrato actualizado exitosamente con ID: {} por usuario: {}",
                                contractId, userId);

                        return updatedContract;
                    })
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado con ID: {}", contractId);
                        return new ContractNotFoundException(contractId);
                    });

        } catch (ContractNotFoundException | ContractValidationException |
                 DuplicateContractNumberException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al actualizar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("actualizar contrato con ID: " + contractId, ex);
        }
    }

    private void updateContractFields(Contract existingContract, Contract updatedDetails) {
        if (updatedDetails.getContractName() != null) {
            existingContract.setContractName(updatedDetails.getContractName());
        }
        if (updatedDetails.getContractNumber() != null) {
            existingContract.setContractNumber(updatedDetails.getContractNumber());
        }
        if (updatedDetails.getAgreedTariff() != null) {
            validateTariff(updatedDetails.getAgreedTariff());
            existingContract.setAgreedTariff(updatedDetails.getAgreedTariff());
        }
        if (updatedDetails.getStartDate() != null) {
            existingContract.setStartDate(updatedDetails.getStartDate());
        }
        if (updatedDetails.getEndDate() != null) {
            existingContract.setEndDate(updatedDetails.getEndDate());
        }
        if (updatedDetails.getStatus() != null) {
            existingContract.setStatus(updatedDetails.getStatus());
        }
    }

    private void validateContractDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            log.error("Fechas de contrato nulas");
            throw new ContractValidationException("Las fechas de inicio y fin son obligatorias");
        }

        if (endDate.isBefore(startDate)) {
            log.error("Fecha de fin anterior a fecha de inicio: {} < {}", endDate, startDate);
            throw new ContractValidationException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        if (startDate.isBefore(LocalDate.now().minusYears(10))) {
            log.warn("Fecha de inicio muy antigua: {}", startDate);
        }
    }

    private void validateTariff(BigDecimal tariff) {
        if (tariff.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Tarifa inválida: {}", tariff);
            throw new ContractValidationException("La tarifa debe ser mayor a cero");
        }

        if (tariff.compareTo(new BigDecimal("1000000000")) > 0) {
            log.error("Tarifa excesivamente alta: {}", tariff);
            throw new ContractValidationException("La tarifa excede el límite permitido");
        }
    }

    private void validateUniqueContractNumber(String contractNumber) {
        if (contractRepository.existsByContractNumber(contractNumber)) {
            log.error("Intento de actualizar a número de contrato duplicado: {}", contractNumber);
            throw new DuplicateContractNumberException(contractNumber);
        }
    }
}