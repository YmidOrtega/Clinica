package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.AuthorizationRequirement;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ContractQueries {

    private final Contracts contracts;

    public ContractQueries(Contracts contracts) {
        this.contracts = contracts;
    }

    public Contract get(UUID uuid) {
        return contracts.findByUuid(uuid).orElseThrow(ContractingException.ContractNotFound::new);
    }

    public List<Contract> ofPayer(UUID payerUuid) {
        return contracts.ofPayer(payerUuid);
    }

    public List<Contract> inForceOn(UUID payerUuid, LocalDate date) {
        return contracts.inForceOn(payerUuid, date);
    }

    public List<ContractTariffException> exceptionsOf(UUID contractUuid) {
        get(contractUuid);
        return contracts.exceptionsOf(contractUuid);
    }

    public List<AuthorizationRequirement> requirementsOf(UUID contractUuid) {
        get(contractUuid);
        return contracts.requirementsOf(contractUuid);
    }

    public List<ContractPackage> packagesOf(UUID contractUuid) {
        get(contractUuid);
        return contracts.packagesOf(contractUuid);
    }
}
