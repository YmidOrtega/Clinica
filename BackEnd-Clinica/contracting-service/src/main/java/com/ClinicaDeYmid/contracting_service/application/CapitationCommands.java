package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.Capitation;
import com.ClinicaDeYmid.contracting_service.domain.CapitatedMember;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import com.ClinicaDeYmid.contracting_service.domain.SettlementPeriodicity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CapitationCommands {

    private static final Logger log = LoggerFactory.getLogger(CapitationCommands.class);

    private final Capitation capitation;
    private final Contracts contracts;
    private final PatientDirectory patients;
    private final TransactionOperations transactions;
    private final Clock clock;

    public CapitationCommands(Capitation capitation, Contracts contracts, PatientDirectory patients,
                              TransactionOperations transactions, Clock clock) {
        this.capitation = capitation;
        this.contracts = contracts;
        this.patients = patients;
        this.transactions = transactions;
        this.clock = clock;
    }

    public FundingAgreement agreeCapitation(UUID contractUuid, BigDecimal perCapitaValue, SettlementPeriodicity periodicity,
                                            String technicalNote, LocalDate validFrom, String actor) {
        return register(contractUuid, validFrom, actor, contract ->
                FundingAgreement.capitation(contract, perCapitaValue, periodicity, technicalNote, validFrom, actor, clock));
    }

    public FundingAgreement agreeGlobalBudget(UUID contractUuid, BigDecimal budgetCeiling, SettlementPeriodicity periodicity,
                                              String technicalNote, LocalDate validFrom, String actor) {
        return register(contractUuid, validFrom, actor, contract ->
                FundingAgreement.globalBudget(contract, budgetCeiling, periodicity, technicalNote, validFrom, actor, clock));
    }

    public FundingAgreement revokeAgreement(UUID agreementUuid, LocalDate from, String actor) {
        return transactions.execute(status -> {
            FundingAgreement agreement = capitation.findAgreementByUuid(agreementUuid)
                    .orElseThrow(ContractingException.FundingAgreementNotFound::new);
            agreement.revoke(from, actor, clock);
            return capitation.save(agreement);
        });
    }

    public Import importMembers(UUID contractUuid, YearMonth period, List<Member> entries, String actor) {
        Import stored = transactions.execute(status -> {
            Contract contract = contracts.findByUuid(contractUuid).orElseThrow(ContractingException.ContractNotFound::new);
            List<CapitatedMember> created = new ArrayList<>();
            int updated = 0;
            int unchanged = 0;
            for (Member entry : entries) {
                CapitatedMember member = CapitatedMember.of(contract, period, entry.documentType(),
                        entry.documentNumber(), entry.fullName(), actor, clock);
                CapitatedMember existing = capitation.findMember(contractUuid, period, member.documentType(),
                        member.documentNumber()).orElse(null);
                if (existing == null) {
                    created.add(member);
                } else if (existing.rename(entry.fullName())) {
                    capitation.save(existing);
                    updated++;
                } else {
                    unchanged++;
                }
            }
            capitation.saveMembers(created);
            log.info("Capitated population loaded: contract={} period={} created={} updated={} unchanged={}",
                    contractUuid, period, created.size(), updated, unchanged);
            return new Import(created.size(), updated, unchanged, 0, 0, 0);
        });
        Verification verification = verify(contractUuid, period, stored.created() + stored.updated() + stored.unchanged());
        return new Import(stored.created(), stored.updated(), stored.unchanged(),
                verification.matched(), verification.unmatched(), verification.unverified());
    }

    public Verification verify(UUID contractUuid, YearMonth period, int limit) {
        int matched = 0;
        int unmatched = 0;
        int unverified = 0;
        for (CapitatedMember member : capitation.unverifiedMembers(contractUuid, period, limit)) {
            PatientLookup lookup = patients.findByDocument(member.documentType(), member.documentNumber());
            switch (lookup) {
                case PatientLookup.Found found -> {
                    member.matched(found.patientUuid(), clock);
                    matched++;
                }
                case PatientLookup.NotFound notFound -> {
                    member.unmatched(clock);
                    unmatched++;
                }
                case PatientLookup.Unavailable unavailable -> {
                    unverified++;
                    continue;
                }
            }
            transactions.executeWithoutResult(status -> capitation.save(member));
        }
        if (unverified > 0) {
            log.warn("Capitated population partially verified: contract={} period={} pending={}", contractUuid, period, unverified);
        }
        return new Verification(matched, unmatched, unverified);
    }

    private FundingAgreement register(UUID contractUuid, LocalDate validFrom, String actor,
                                      java.util.function.Function<Contract, FundingAgreement> factory) {
        return transactions.execute(status -> {
            Contract contract = contracts.findByUuid(contractUuid).orElseThrow(ContractingException.ContractNotFound::new);
            FundingAgreement agreement = factory.apply(contract);
            capitation.agreementInForce(contractUuid, validFrom)
                    .ifPresent(current -> {
                        current.revoke(validFrom, actor, clock);
                        capitation.save(current);
                    });
            log.info("Funding agreement registered: contract={} modality={}", contractUuid, contract.modality());
            return capitation.save(agreement);
        });
    }

    public record Member(String documentType, String documentNumber, String fullName) {
    }

    public record Import(int created, int updated, int unchanged, int matched, int unmatched, int unverified) {
    }

    public record Verification(int matched, int unmatched, int unverified) {
    }
}
