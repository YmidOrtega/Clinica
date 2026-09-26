package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record Copayment(UUID authorizationUuid, String authorizationNumber, BigDecimal amount, LocalDate validFrom,
                        LocalDate validTo, Set<UUID> items, boolean coversEverything) {

    public Copayment {
        DomainRules.required(authorizationUuid, "authorizationUuid");
        amount = amount == null ? Money.ZERO : Money.of(amount);
        items = items == null ? Set.of() : Set.copyOf(items);
    }

    boolean usedBy(SaleLine line) {
        boolean inForce = (validFrom == null || !line.serviceDate().isBefore(validFrom))
                && (validTo == null || !line.serviceDate().isAfter(validTo));
        if (line.origin() instanceof LineOrigin.Authorized authorized && authorized.authorizationUuid().equals(authorizationUuid)) {
            return true;
        }
        return inForce && (coversEverything || items.contains(line.service().portfolioItemUuid()));
    }
}
