package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractModality;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.Payers;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffManuals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ContractCommands {

    private static final Logger log = LoggerFactory.getLogger(ContractCommands.class);

    private final Contracts contracts;
    private final Payers payers;
    private final TariffManuals manuals;
    private final TransactionOperations transactions;
    private final Clock clock;

    public ContractCommands(Contracts contracts, Payers payers, TariffManuals manuals,
                            TransactionOperations transactions, Clock clock) {
        this.contracts = contracts;
        this.payers = payers;
        this.manuals = manuals;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Contract draft(UUID payerUuid, String number, String name, ContractModality modality,
                          LocalDate validFrom, LocalDate validTo) {
        return transactions.execute(status -> {
            Payer payer = payers.findByUuid(payerUuid).orElseThrow(ContractingException.PayerNotFound::new);
            Contract contract = Contract.draft(payer, number, name, modality, validFrom, validTo);
            if (contracts.findByNumber(payerUuid, contract.number()).isPresent()) {
                throw new ContractingException.ContractNumberAlreadyUsed();
            }
            log.info("Contract drafted: uuid={} payer={} number={}", contract.uuid(), payer.nit().number(), contract.number());
            return contracts.save(contract);
        });
    }

    public Contract agreeTariff(UUID uuid, long expectedVersion, UUID tariffVersionUuid, BigDecimal factor) {
        return modify(uuid, expectedVersion, contract -> {
            TariffManualVersion version = manuals.findVersionByUuid(tariffVersionUuid)
                    .orElseThrow(ContractingException.TariffVersionNotFound::new);
            contract.agreeTariff(version, factor);
        });
    }

    public Contract rename(UUID uuid, long expectedVersion, String name) {
        return modify(uuid, expectedVersion, contract -> contract.rename(name));
    }

    public Contract extendTo(UUID uuid, long expectedVersion, LocalDate validTo) {
        return modify(uuid, expectedVersion, contract -> contract.extendTo(validTo));
    }

    public Contract activate(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, contract -> contract.activate(clock));
    }

    public Contract suspend(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, contract -> contract.suspend(reason, clock));
    }

    public Contract terminate(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, contract -> contract.terminate(reason, clock));
    }

    public ContractTariffException registerException(UUID contractUuid, String cupsCode, BigDecimal agreedPrice,
                                                     String reason, LocalDate validFrom, String actor) {
        return transactions.execute(status -> {
            Contract contract = contract(contractUuid);
            contracts.exceptionFor(contractUuid, cupsCode, validFrom)
                    .ifPresent(current -> {
                        current.revoke(validFrom, "Reemplazada por una excepción nueva", actor, clock);
                        contracts.save(current);
                    });
            ContractTariffException registered = ContractTariffException.register(contract, cupsCode, agreedPrice,
                    reason, validFrom, actor, clock);
            log.info("Contract exception registered: contract={} code={}", contractUuid, cupsCode);
            return contracts.save(registered);
        });
    }

    public ContractTariffException revokeException(UUID exceptionUuid, LocalDate from, String reason, String actor) {
        return transactions.execute(status -> {
            ContractTariffException exception = contracts.findExceptionByUuid(exceptionUuid)
                    .orElseThrow(ContractingException.ExceptionNotFound::new);
            exception.revoke(from, reason, actor, clock);
            return contracts.save(exception);
        });
    }

    public ContractPackage agreePackage(UUID contractUuid, String code, String name, BigDecimal price,
                                        Set<String> includedCodes, LocalDate validFrom, String actor) {
        return transactions.execute(status -> {
            Contract contract = contract(contractUuid);
            ContractPackage agreed = ContractPackage.agree(contract, code, name, price, includedCodes, validFrom, actor, clock);
            contracts.packagesOf(contractUuid).stream()
                    .filter(existing -> existing.code().equals(agreed.code()) && existing.appliesOn(validFrom))
                    .findFirst()
                    .ifPresent(existing -> {
                        existing.revoke(validFrom, actor, clock);
                        contracts.save(existing);
                    });
            log.info("Contract package agreed: contract={} code={} items={}", contractUuid, agreed.code(),
                    agreed.includedCodes().size());
            return contracts.save(agreed);
        });
    }

    public ContractPackage revokePackage(UUID packageUuid, LocalDate from, String actor) {
        return transactions.execute(status -> {
            ContractPackage agreed = contracts.findPackageByUuid(packageUuid)
                    .orElseThrow(ContractingException.PackageNotFound::new);
            agreed.revoke(from, actor, clock);
            return contracts.save(agreed);
        });
    }

    private Contract contract(UUID uuid) {
        return contracts.findByUuid(uuid).orElseThrow(ContractingException.ContractNotFound::new);
    }

    private Contract modify(UUID uuid, long expectedVersion, Consumer<Contract> change) {
        return transactions.execute(status -> {
            Contract contract = contract(uuid);
            if (contract.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            change.accept(contract);
            return contracts.save(contract);
        });
    }
}
