package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.service.FeeCommands;
import com.ClinicaDeYmid.practitioners_service.shared.FeeBasis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class FeeRequests {

    private FeeRequests() {
    }

    record ProcedureFee(String serviceCode, BigDecimal amount) {

        FeeCommands.ProcedureFee toCommand() {
            return new FeeCommands.ProcedureFee(serviceCode, amount);
        }
    }

    record NewAgreement(FeeBasis basis, BigDecimal amount, List<ProcedureFee> procedures, LocalDate validFrom,
                        String note) {

        FeeCommands.NewAgreement toCommand() {
            return new FeeCommands.NewAgreement(basis, amount,
                    procedures == null ? List.of() : procedures.stream().map(ProcedureFee::toCommand).toList(),
                    validFrom, note);
        }
    }
}
