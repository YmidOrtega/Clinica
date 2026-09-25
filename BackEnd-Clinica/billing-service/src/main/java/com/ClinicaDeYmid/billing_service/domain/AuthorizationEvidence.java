package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthorizationEvidence(List<Grant> grants, List<EmergencyPeriod> emergencies) {

    public record Grant(UUID uuid, String number, LocalDate validFrom, LocalDate validTo, Set<UUID> items,
                        boolean coversEverything) {

        public Grant {
            DomainRules.required(uuid, "uuid");
            items = items == null ? Set.of() : Set.copyOf(items);
        }

        boolean validOn(LocalDate day) {
            return (validFrom == null || !day.isBefore(validFrom)) && (validTo == null || !day.isAfter(validTo));
        }

        boolean covers(SaleLine line) {
            if (!validOn(line.serviceDate())) {
                return false;
            }
            if (coversEverything || items.contains(line.service().portfolioItemUuid())) {
                return true;
            }
            return line.origin() instanceof LineOrigin.Authorized authorized
                    && authorized.authorizationUuid().equals(uuid);
        }
    }

    public record EmergencyPeriod(Instant from, Instant to) {

        public EmergencyPeriod {
            DomainRules.required(from, "from");
        }

        boolean includes(LocalDate day, ZoneId zone) {
            LocalDate start = LocalDate.ofInstant(from, zone);
            LocalDate end = to == null ? null : LocalDate.ofInstant(to, zone);
            return !day.isBefore(start) && (end == null || !day.isAfter(end));
        }
    }

    public AuthorizationEvidence {
        grants = List.copyOf(grants);
        emergencies = List.copyOf(emergencies);
    }

    public static AuthorizationEvidence none() {
        return new AuthorizationEvidence(List.of(), List.of());
    }

    public AuthorizationCheck check(SaleLine line, boolean required, ZoneId zone) {
        if (!required) {
            return AuthorizationCheck.NOT_REQUIRED;
        }
        if (grants.stream().anyMatch(grant -> grant.covers(line))) {
            return AuthorizationCheck.AUTHORIZED;
        }
        if (emergencies.stream().anyMatch(period -> period.includes(line.serviceDate(), zone))) {
            return AuthorizationCheck.EMERGENCY_EXEMPT;
        }
        return AuthorizationCheck.MISSING;
    }
}
