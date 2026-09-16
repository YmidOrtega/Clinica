package com.ClinicaDeYmid.contracting_service.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Contracts {

    Contract save(Contract contract);

    Optional<Contract> findByUuid(UUID uuid);

    Optional<Contract> findByNumber(UUID payerUuid, String number);

    List<Contract> ofPayer(UUID payerUuid);

    List<Contract> inForceOn(UUID payerUuid, LocalDate date);

    ContractTariffException save(ContractTariffException exception);

    Optional<ContractTariffException> findExceptionByUuid(UUID uuid);

    List<ContractTariffException> exceptionsOf(UUID contractUuid);

    Optional<ContractTariffException> exceptionFor(UUID contractUuid, String cupsCode, LocalDate date);

    ContractPackage save(ContractPackage agreed);

    Optional<ContractPackage> findPackageByUuid(UUID uuid);

    List<ContractPackage> packagesOf(UUID contractUuid);

    List<ContractPackage> packagesInForce(UUID contractUuid, LocalDate date);
}
