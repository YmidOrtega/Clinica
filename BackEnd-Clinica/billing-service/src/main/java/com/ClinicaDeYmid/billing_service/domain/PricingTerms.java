package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PricingTerms(UUID contractUuid, String contractNumber, UUID payerUuid, Map<UUID, LinePrice> quoted,
                           List<PackageCharge> packages) {

    public PricingTerms {
        quoted = Map.copyOf(quoted);
        packages = List.copyOf(packages);
        contractNumber = DomainRules.optionalText(contractNumber, "contractNumber", 40);
    }

    public static PricingTerms withoutContract() {
        return new PricingTerms(null, null, null, Map.of(), List.of());
    }
}
