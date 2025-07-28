package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.ContractDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetContractService {

    private final ContractRepository contractRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Transactional(readOnly = true)
    public ContractDto getContractById(Long contractId) {
        validateContractIdInput(contractId);

        log.info("Iniciando consulta de contrato con ID: {}", contractId);

        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> {
                        log.warn("Contrato no encontrado con ID: {}", contractId);
                        return new ContractNotFoundException(contractId);
                    });

            log.info("Contrato encontrado exitosamente - ID: {}, Número: {}",
                    contractId, contract.getContractNumber());

            return healthProviderMapper.toContractDto(contract);

        } catch (QueryTimeoutException ex) {
            log.error("Timeout al consultar contrato con ID: {} - Tiempo de espera agotado", contractId, ex);
            throw new ContractDataAccessException(
                    "consultar contrato con ID: " + contractId + " (timeout de base de datos)", ex);

        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad de datos al consultar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException(
                    "consultar contrato con ID: " + contractId + " (violación de integridad)", ex);

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al consultar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException(
                    "consultar contrato con ID: " + contractId, ex);

        } catch (Exception ex) {
            log.error("Error inesperado al consultar contrato con ID: {}", contractId, ex);
            throw new ContractDataAccessException(
                    "consultar contrato con ID: " + contractId + " (error inesperado)", ex);
        }
    }

    private void validateContractIdInput(Long contractId) {
        if (contractId == null) {
            log.error("ID de contrato proporcionado es nulo");
            throw new IllegalArgumentException("El ID del contrato no puede ser nulo");
        }

        if (contractId <= 0) {
            log.error("ID de contrato inválido: {}", contractId);
            throw new IllegalArgumentException("El ID del contrato debe ser un número positivo");
        }
    }
}
