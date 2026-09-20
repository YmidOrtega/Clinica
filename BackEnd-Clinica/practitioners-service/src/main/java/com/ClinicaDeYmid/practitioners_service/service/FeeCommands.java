package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.shared.FeeBasis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FeeCommands {

    private FeeCommands() {
    }

    public record ProcedureFee(String serviceCode, BigDecimal amount) {
    }

    public record NewAgreement(FeeBasis basis, BigDecimal amount, List<ProcedureFee> procedures, LocalDate validFrom,
                               String note) {

        public NewAgreement {
            procedures = procedures == null ? List.of() : List.copyOf(procedures);
        }
    }
}
