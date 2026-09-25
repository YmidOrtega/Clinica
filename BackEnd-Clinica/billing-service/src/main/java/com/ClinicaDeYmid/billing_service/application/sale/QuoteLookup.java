package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.PriceOrigin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public sealed interface QuoteLookup {

    record Quoted(String contractNumber, UUID payerUuid, List<Service> services, List<Package> packages)
            implements QuoteLookup {

        public Quoted {
            services = List.copyOf(services);
            packages = List.copyOf(packages);
        }
    }

    record Refused(String detail) implements QuoteLookup {
    }

    record Unavailable() implements QuoteLookup {
    }

    record Service(String cupsCode, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, PriceOrigin origin,
                   UUID referenceUuid, String referenceCode) {
    }

    record Package(UUID uuid, String code, String name, BigDecimal price) {
    }
}
