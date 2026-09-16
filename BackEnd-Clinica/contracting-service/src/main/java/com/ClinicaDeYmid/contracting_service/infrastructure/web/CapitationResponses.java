package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.CapitationCommands;
import com.ClinicaDeYmid.contracting_service.domain.CapitatedMember;
import com.ClinicaDeYmid.contracting_service.domain.ContractModality;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import com.ClinicaDeYmid.contracting_service.domain.MemberVerification;
import com.ClinicaDeYmid.contracting_service.domain.SettlementPeriodicity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

final class CapitationResponses {

    private CapitationResponses() {
    }

    record AgreementView(UUID uuid, UUID contractUuid, ContractModality modality, BigDecimal perCapitaValue,
                         BigDecimal budgetCeiling, SettlementPeriodicity periodicity, String periodicityLabel,
                         String technicalNote, LocalDate validFrom, LocalDate revokedFrom, Instant registeredAt,
                         String registeredBy) {

        static AgreementView from(FundingAgreement agreement) {
            return new AgreementView(agreement.uuid(), agreement.contract().uuid(), agreement.modality(),
                    agreement.perCapitaValue(), agreement.budgetCeiling(), agreement.periodicity(),
                    agreement.periodicity().label(), agreement.technicalNote(), agreement.validFrom(),
                    agreement.revokedFrom(), agreement.registeredAt(), agreement.registeredBy());
        }
    }

    record MemberView(UUID uuid, YearMonth period, String documentType, String documentNumber, String fullName,
                      UUID patientUuid, MemberVerification verification, String verificationLabel, Instant verifiedAt) {

        static MemberView from(CapitatedMember member) {
            return new MemberView(member.uuid(), member.period(), member.documentType(), member.documentNumber(),
                    member.fullName(), member.patientUuid(), member.verification(), member.verification().label(),
                    member.verifiedAt());
        }
    }

    record CoverageView(UUID contractUuid, String contractNumber, UUID payerUuid, String payerName, YearMonth period,
                        UUID patientUuid, MemberVerification verification) {

        static CoverageView from(CapitatedMember member) {
            return new CoverageView(member.contract().uuid(), member.contract().number(),
                    member.contract().payer().uuid(), member.contract().payer().socialReason(), member.period(),
                    member.patientUuid(), member.verification());
        }
    }

    record ImportView(int created, int updated, int unchanged, int matched, int unmatched, int unverified) {

        static ImportView from(CapitationCommands.Import result) {
            return new ImportView(result.created(), result.updated(), result.unchanged(), result.matched(),
                    result.unmatched(), result.unverified());
        }
    }

    record VerificationView(int matched, int unmatched, int unverified) {

        static VerificationView from(CapitationCommands.Verification result) {
            return new VerificationView(result.matched(), result.unmatched(), result.unverified());
        }
    }
}
