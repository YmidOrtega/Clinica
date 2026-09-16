package com.ClinicaDeYmid.contracting_service.domain;

import com.ClinicaDeYmid.contracting_service.support.PayerFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingAgreementTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void capitationKeepsThePerCapitaValueAndItsValidity() {
        FundingAgreement agreement = FundingAgreement.capitation(contract(ContractModality.CAPITATION),
                new BigDecimal("38500.5"), SettlementPeriodicity.MONTHLY, "Nota técnica del contrato", START,
                "actor", PayerFixtures.CLOCK);

        assertThat(agreement.perCapitaValue()).isEqualByComparingTo("38500.50");
        assertThat(agreement.budgetCeiling()).isNull();
        assertThat(agreement.appliesOn(LocalDate.of(2026, 6, 1))).isTrue();

        agreement.revoke(LocalDate.of(2026, 7, 1), "actor", PayerFixtures.CLOCK);

        assertThat(agreement.appliesOn(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(agreement.appliesOn(LocalDate.of(2026, 6, 30))).isTrue();
        assertThatThrownBy(() -> agreement.revoke(LocalDate.of(2026, 8, 1), "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.FundingAgreementAlreadyRevoked.class);
    }

    @Test
    void eachAgreementBelongsToItsOwnModality() {
        assertThatThrownBy(() -> FundingAgreement.capitation(contract(ContractModality.GLOBAL_BUDGET),
                BigDecimal.TEN, SettlementPeriodicity.MONTHLY, "Nota técnica del contrato", START, "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.FundingNotApplicable.class);

        assertThatThrownBy(() -> FundingAgreement.globalBudget(contract(ContractModality.CAPITATION),
                BigDecimal.TEN, SettlementPeriodicity.MONTHLY, "Nota técnica del contrato", START, "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.FundingNotApplicable.class);
    }

    @Test
    void refusesAmountsThatAreNotMoney() {
        assertThatThrownBy(() -> FundingAgreement.capitation(contract(ContractModality.CAPITATION),
                BigDecimal.ZERO, SettlementPeriodicity.MONTHLY, "Nota técnica del contrato", START, "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.InvalidData.class);

        assertThatThrownBy(() -> FundingAgreement.capitation(contract(ContractModality.CAPITATION),
                new BigDecimal("1000.12345"), SettlementPeriodicity.MONTHLY, "Nota técnica del contrato", START,
                "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.InvalidData.class);
    }

    @Test
    void capitatedMembersOnlyExistForCapitationContracts() {
        assertThatThrownBy(() -> CapitatedMember.of(contract(ContractModality.EVENT), YearMonth.of(2026, 3),
                "CC", "1020304050", "Ana María Rojas", "actor", PayerFixtures.CLOCK))
                .isInstanceOf(ContractingException.CapitationNotApplicable.class);
    }

    @Test
    void aMemberStartsUnverifiedAndKeepsItsLastVerification() {
        CapitatedMember member = CapitatedMember.of(contract(ContractModality.CAPITATION), YearMonth.of(2026, 3),
                "cc", "1020304050", "Ana María Rojas", "actor", PayerFixtures.CLOCK);

        assertThat(member.verification()).isEqualTo(MemberVerification.UNVERIFIED);
        assertThat(member.documentType()).isEqualTo("CC");
        assertThat(member.verifiedAt()).isNull();

        member.matched(java.util.UUID.randomUUID(), PayerFixtures.CLOCK);

        assertThat(member.verification()).isEqualTo(MemberVerification.MATCHED);
        assertThat(member.patientUuid()).isNotNull();

        member.unmatched(PayerFixtures.CLOCK);

        assertThat(member.patientUuid()).isNull();
        assertThat(member.verification()).isEqualTo(MemberVerification.UNMATCHED);
    }

    @Test
    void renamingReportsWhetherTheNameChanged() {
        CapitatedMember member = CapitatedMember.of(contract(ContractModality.CAPITATION), YearMonth.of(2026, 3),
                "CC", "1020304050", "Ana María Rojas", "actor", PayerFixtures.CLOCK);

        assertThat(member.rename("Ana María Rojas")).isFalse();
        assertThat(member.rename("Ana María Rojas Pérez")).isTrue();
        assertThat(member.fullName()).isEqualTo("Ana María Rojas Pérez");
    }

    private static Contract contract(ContractModality modality) {
        return Contract.draft(PayerFixtures.active(), "CNT-1", "Contrato de prueba", modality, START,
                LocalDate.of(2026, 12, 31));
    }
}
