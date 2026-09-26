package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.Invoice;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SharedPaymentAllocation {

    private SharedPaymentAllocation() {
    }

    public static List<Invoice> forUnit(AccountSummary summary, AccountSummary.Unit unit, List<Invoice> shared) {
        Set<String> ofUnit = authorizations(List.of(unit));
        Set<String> ofAccount = authorizations(summary.units());
        boolean takesTheRest = unit.kind() == AccountSummary.UnitKind.ACCOUNT || summary.units().size() == 1;
        return shared.stream()
                .filter(payment -> payment.sharedPaymentKind().creditable())
                .filter(payment -> payment.authorizationNumber() != null
                        ? ofUnit.contains(payment.authorizationNumber())
                        || (takesTheRest && !ofAccount.contains(payment.authorizationNumber()))
                        : takesTheRest)
                .toList();
    }

    public static List<Invoice> unallocated(AccountSummary summary, List<Invoice> shared) {
        Set<Invoice> allocated = summary.units().stream()
                .flatMap(unit -> forUnit(summary, unit, shared).stream())
                .collect(Collectors.toSet());
        return shared.stream().filter(payment -> !allocated.contains(payment)).toList();
    }

    private static Set<String> authorizations(List<AccountSummary.Unit> units) {
        return units.stream().flatMap(unit -> unit.copayments().stream())
                .map(Copayment::authorizationNumber)
                .filter(number -> number != null)
                .collect(Collectors.toSet());
    }
}
