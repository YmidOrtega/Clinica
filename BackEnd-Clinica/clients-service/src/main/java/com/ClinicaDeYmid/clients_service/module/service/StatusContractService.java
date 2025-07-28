package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractNotFoundForStatusException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractAlreadyActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractAlreadyInactiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDeletionRestrictedException;
import com.ClinicaDeYmid.clients_service.infra.exception.ContractDataAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusContractService {

    private final ContractRepository contractRepository;

    @Transactional
    public void deleteContract(Long contractId) {
        log.info("Iniciando eliminación de contrato con ID: {}", contractId);

        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new ContractNotFoundForStatusException(contractId, "eliminar"));

            if (hasBusinessRestrictions(contract)) {
                throw new ContractDeletionRestrictedException(contractId,
                        "El contrato tiene restricciones de negocio que impiden su eliminación");
            }

            contractRepository.deleteById(contractId);
            log.info("Contrato eliminado exitosamente con ID: {}", contractId);

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
    public Contract activateContract(Long contractId) {
        log.info("Iniciando activación de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(contract -> {
                        if (Boolean.TRUE.equals(contract.getActive())) {
                            log.warn("Intento de activar contrato ya activo con ID: {}", contractId);
                            throw new ContractAlreadyActiveException(contractId, contract.getContractNumber());
                        }

                        contract.setActive(true);
                        Contract activatedContract = contractRepository.save(contract);

                        log.info("Contrato activado exitosamente con ID: {}", contractId);
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
    public Contract deactivateContract(Long contractId) {
        log.info("Iniciando desactivación de contrato con ID: {}", contractId);

        try {
            return contractRepository.findById(contractId)
                    .map(contract -> {
                        if (Boolean.FALSE.equals(contract.getActive())) {
                            log.warn("Intento de desactivar contrato ya inactivo con ID: {}", contractId);
                            throw new ContractAlreadyInactiveException(contractId, contract.getContractNumber());
                        }

                        contract.setActive(false);
                        Contract deactivatedContract = contractRepository.save(contract);

                        log.info("Contrato desactivado exitosamente con ID: {}", contractId);
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

    private boolean hasBusinessRestrictions(Contract contract) {
        // Implementar lógica de restricciones de negocio
        // Por ejemplo: verificar si tiene servicios cubiertos activos, etc.
        return contract.getCoveredServices() != null && !contract.getCoveredServices().isEmpty();
    }
}
