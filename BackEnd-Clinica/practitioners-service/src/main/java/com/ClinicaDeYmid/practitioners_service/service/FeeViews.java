package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.repository.entity.FeeAgreement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FeeViews {

    private FeeViews() {
    }

    public record ProcedureFeeView(String serviceCode, BigDecimal amount) {
    }

    public record AgreementView(UUID uuid, String basis, String basisLabel, BigDecimal amount,
                                List<ProcedureFeeView> procedures, LocalDate validFrom, LocalDate revokedOn,
                                boolean inForce, String note, Instant agreedAt, String agreedBy) {

        static AgreementView of(FeeAgreement agreement, LocalDate today) {
            return new AgreementView(agreement.uuid(), agreement.basis().name(), agreement.basis().label(),
                    agreement.amount(),
                    agreement.lines().stream()
                            .map(line -> new ProcedureFeeView(line.serviceCode(), line.amount()))
                            .toList(),
                    agreement.validFrom(), agreement.revokedOn(), agreement.inForceOn(today), agreement.note(),
                    agreement.createdAt(), agreement.createdBy());
        }
    }
}
