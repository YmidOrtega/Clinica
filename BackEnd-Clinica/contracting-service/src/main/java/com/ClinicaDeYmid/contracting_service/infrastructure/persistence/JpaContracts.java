package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractStatus;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaContracts implements Contracts {

    private final ContractJpaRepository contracts;
    private final ContractTariffExceptionJpaRepository exceptions;
    private final ContractPackageJpaRepository packages;

    JpaContracts(ContractJpaRepository contracts, ContractTariffExceptionJpaRepository exceptions,
                 ContractPackageJpaRepository packages) {
        this.contracts = contracts;
        this.exceptions = exceptions;
        this.packages = packages;
    }

    @Override
    public Contract save(Contract contract) {
        return contracts.saveAndFlush(contract);
    }

    @Override
    public Optional<Contract> findByUuid(UUID uuid) {
        return contracts.findByUuid(uuid);
    }

    @Override
    public Optional<Contract> findByNumber(UUID payerUuid, String number) {
        return contracts.findByNumber(payerUuid, number);
    }

    @Override
    public List<Contract> ofPayer(UUID payerUuid) {
        return contracts.findByPayer(payerUuid);
    }

    @Override
    public List<Contract> inForceOn(UUID payerUuid, LocalDate date) {
        return contracts.findInForceOn(payerUuid, date, ContractStatus.Code.ACTIVE);
    }

    @Override
    public ContractTariffException save(ContractTariffException exception) {
        return exceptions.saveAndFlush(exception);
    }

    @Override
    public Optional<ContractTariffException> findExceptionByUuid(UUID uuid) {
        return exceptions.findByUuid(uuid);
    }

    @Override
    public List<ContractTariffException> exceptionsOf(UUID contractUuid) {
        return exceptions.findByContract(contractUuid);
    }

    @Override
    public Optional<ContractTariffException> exceptionFor(UUID contractUuid, String cupsCode, LocalDate date) {
        return exceptions.findApplying(contractUuid, cupsCode, date).stream().findFirst();
    }

    @Override
    public ContractPackage save(ContractPackage agreed) {
        return packages.saveAndFlush(agreed);
    }

    @Override
    public Optional<ContractPackage> findPackageByUuid(UUID uuid) {
        return packages.findByUuid(uuid);
    }

    @Override
    public List<ContractPackage> packagesOf(UUID contractUuid) {
        return packages.findByContract(contractUuid);
    }

    @Override
    public List<ContractPackage> packagesInForce(UUID contractUuid, LocalDate date) {
        return packages.findApplying(contractUuid, date);
    }
}
