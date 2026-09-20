package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.service.FeeViews;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class FeeResponses {

    private FeeResponses() {
    }

    record ProcedureFeeView(String serviceCode, BigDecimal amount) {
    }

    record AgreementView(UUID uuid, String basis, String basisLabel, BigDecimal amount,
                         List<ProcedureFeeView> procedures, LocalDate validFrom, LocalDate revokedOn, boolean inForce,
                         String note, Instant agreedAt, String agreedBy) {

        static AgreementView from(FeeViews.AgreementView agreement) {
            return new AgreementView(agreement.uuid(), agreement.basis(), agreement.basisLabel(), agreement.amount(),
                    agreement.procedures().stream()
                            .map(procedure -> new ProcedureFeeView(procedure.serviceCode(), procedure.amount()))
                            .toList(),
                    agreement.validFrom(), agreement.revokedOn(), agreement.inForce(), agreement.note(),
                    agreement.agreedAt(), agreement.agreedBy());
        }
    }
}
