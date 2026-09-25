package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                           List<PackageCharge> packages, Set<UUID> requiringAuthorization,
                           AuthorizationEvidence authorizations) {

    public PricingTerms {
        quoted = Map.copyOf(quoted);
        packages = List.copyOf(packages);
        requiringAuthorization = Set.copyOf(requiringAuthorization);
        authorizations = authorizations == null ? AuthorizationEvidence.none() : authorizations;
        contractNumber = DomainRules.optionalText(contractNumber, "contractNumber", 40);
    }

    public PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                        List<PackageCharge> packages) {
        this(contractUuid, contractNumber, payerUuid, quoted, packages, Set.of(), AuthorizationEvidence.none());
    }

    public static PricingTerms withoutContract() {
        return new PricingTerms(null, null, null, Map.of(), List.of());
    }
}
