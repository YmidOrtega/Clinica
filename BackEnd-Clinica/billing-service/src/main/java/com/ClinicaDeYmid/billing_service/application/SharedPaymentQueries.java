package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Money;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SharedPaymentQueries {

    public record UnitShare(AccountSummary.Unit unit, List<Invoice> invoiced, BigDecimal pending) {
    }

    public record Overview(AccountSummary summary, SharedPaymentKind proposedKind, List<UnitShare> units,
                           List<Invoice> unallocated) {
    }

    private final AccountSummaries summaries;
    private final Invoices invoices;
    private final PatientDirectory patients;

    public SharedPaymentQueries(AccountSummaries summaries, Invoices invoices, PatientDirectory patients) {
        this.summaries = summaries;
        this.invoices = invoices;
        this.patients = patients;
    }

    public Overview overview(String admissionNumber) {
        AccountSummaries.Context context = summaries.context(admissionNumber);
        AccountSummary summary = context.summary();
        List<Invoice> shared = invoices.sharedPaymentsOf(summary.account().uuid());
        SharedPaymentKind proposed = switch (patients.patient(summary.account().patientUuid())) {
            case PatientLookup.Found found -> SharedPaymentCommands.proposedKind(found.patient(), summary.account());
            case PatientLookup.NotFound ignored -> SharedPaymentKind.COPAYMENT;
            case PatientLookup.Unavailable ignored -> SharedPaymentKind.COPAYMENT;
        };
        List<UnitShare> units = summary.units().stream().map(unit -> {
            List<Invoice> invoiced = SharedPaymentAllocation.forUnit(summary, unit, shared);
            BigDecimal paid = invoiced.stream().map(Invoice::grossTotal).reduce(Money.ZERO, BigDecimal::add);
            return new UnitShare(unit, invoiced, Money.of(unit.patientShare().subtract(paid)));
        }).toList();
        return new Overview(summary, proposed, units, SharedPaymentAllocation.unallocated(summary, shared));
    }
}
