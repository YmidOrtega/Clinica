package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.CapitationCommands;
import com.ClinicaDeYmid.contracting_service.domain.SettlementPeriodicity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class CapitationRequests {

    private static final String REQUIRED = "es obligatorio";

    private CapitationRequests() {
    }

    record CapitationAgreement(@NotNull(message = REQUIRED) BigDecimal perCapitaValue,
                               @NotNull(message = REQUIRED) SettlementPeriodicity periodicity,
                               String technicalNote,
                               @NotNull(message = REQUIRED) LocalDate validFrom) {
    }

    record BudgetAgreement(@NotNull(message = REQUIRED) BigDecimal budgetCeiling,
                           @NotNull(message = REQUIRED) SettlementPeriodicity periodicity,
                           String technicalNote,
                           @NotNull(message = REQUIRED) LocalDate validFrom) {
    }

    record Member(String documentType, String documentNumber, String fullName) {

        CapitationCommands.Member toMember() {
            return new CapitationCommands.Member(documentType, documentNumber, fullName);
        }
    }

    record MemberImport(@NotEmpty(message = "debe traer al menos un afiliado")
                        @Size(max = 20000, message = "no puede superar 20000 afiliados por carga")
                        @Valid List<Member> members) {

        List<CapitationCommands.Member> toMembers() {
            return members.stream().map(Member::toMember).toList();
        }
    }

    record Revocation(LocalDate from) {
    }
}
