package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.module.dto.UpdateContractDto;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundException;
import com.ClinicaDeYmid.clients_service.infra.exception.UpdateContractNumberConflictException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractUpdateException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateContractService {

    private final ContractRepository contractRepository;

    @Transactional
    public Contract updateContract(Long contractId, UpdateContractDto updateDto) {
        log.info("Iniciando actualización de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(existingContract -> {
                        updateContractFields(existingContract, updateDto);
                        validateAndUpdateContractNumber(existingContract, updateDto, contractId);

                        Contract updatedContract = contractRepository.save(existingContract);

                        log.info("Contrato actualizado exitosamente con ID: {} y número: {}",
                                updatedContract.getId(), updatedContract.getContractNumber());

                        return updatedContract;
                    })
                    .orElseThrow(() -> {
                        log.error("Contrato no encontrado con ID: {}", contractId);
                        return new ContractNotFoundException(contractId);
                    });

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al actualizar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException("actualizar contrato con ID: " + contractId, ex);
        } catch (ContractNotFoundException | UpdateContractNumberConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al actualizar contrato con ID: {}", contractId, ex);
            throw new ContractUpdateException(contractId, ex.getMessage(), ex);
        }
    }

    private void updateContractFields(Contract existingContract, UpdateContractDto updateDto) {
        if (updateDto.contractName() != null) {
            existingContract.setContractName(updateDto.contractName());
        }
        if (updateDto.agreedTariff() != null) {
            existingContract.setAgreedTariff(updateDto.agreedTariff());
        }
        if (updateDto.startDate() != null) {
            existingContract.setStartDate(updateDto.startDate());
        }
        if (updateDto.endDate() != null) {
            existingContract.setEndDate(updateDto.endDate());
        }
        if (updateDto.status() != null) {
            existingContract.setStatus(updateDto.status());
        }
        if (updateDto.active() != null) {
            existingContract.setActive(updateDto.active());
        }
    }

    private void validateAndUpdateContractNumber(Contract existingContract, UpdateContractDto updateDto, Long contractId) {
        if (updateDto.contractNumber() != null &&
                !updateDto.contractNumber().equals(existingContract.getContractNumber())) {

            String newContractNumber = updateDto.contractNumber();

            contractRepository.findByContractNumber(newContractNumber)
                    .ifPresent(conflictingContract -> {
                        if (!conflictingContract.getId().equals(contractId)) {
                            log.warn("Intento de actualizar con número de contrato conflictivo: {} ya existe para ID: {}",
                                    newContractNumber, conflictingContract.getId());
                            throw new UpdateContractNumberConflictException(newContractNumber,
                                    conflictingContract.getId().toString());
                        }
                    });

            existingContract.setContractNumber(newContractNumber);
            log.info("Número de contrato actualizado a: {}", newContractNumber);
        }
    }
}