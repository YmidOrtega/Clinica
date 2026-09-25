package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                           List<PackageCharge> packages, Set<UUID> requiringAuthorization,
                           AuthorizationEvidence authorizations, Set<UUID> surgical) {

    public PricingTerms {
        quoted = Map.copyOf(quoted);
        packages = List.copyOf(packages);
        requiringAuthorization = Set.copyOf(requiringAuthorization);
        surgical = surgical == null ? Set.of() : Set.copyOf(surgical);
        authorizations = authorizations == null ? AuthorizationEvidence.none() : authorizations;
        contractNumber = DomainRules.optionalText(contractNumber, "contractNumber", 40);
    }

    public PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                        List<PackageCharge> packages) {
        this(contractUuid, contractNumber, payerUuid, quoted, packages, Set.of(), AuthorizationEvidence.none(), Set.of());
    }

    public PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                        List<PackageCharge> packages, Set<UUID> requiringAuthorization,
                        AuthorizationEvidence authorizations) {
        this(contractUuid, contractNumber, payerUuid, quoted, packages, requiringAuthorization, authorizations, Set.of());
    }

    public static PricingTerms withoutContract() {
        return new PricingTerms(null, null, null, Map.of(), List.of());
    }
}
