package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PricingQueries;
import com.ClinicaDeYmid.contracting_service.domain.PriceOrigin;
import com.ClinicaDeYmid.contracting_service.domain.PricedService;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class PricingResponses {

    private PricingResponses() {
    }

    record QuoteView(UUID contractUuid, String contractNumber, UUID payerUuid, String payerName, LocalDate on,
                     TariffReferenceView tariff, List<ServiceView> services, List<PackageView> packages, BigDecimal total) {

        static QuoteView from(PricingQueries.Quote quote) {
            return new QuoteView(quote.contract().uuid(), quote.contract().number(), quote.contract().payer().uuid(),
                    quote.contract().payer().socialReason(), quote.date(),
                    TariffReferenceView.from(quote.tariffVersion(), quote.contract().tariffFactor()),
                    quote.services().stream().map(ServiceView::from).toList(),
                    quote.packages().stream().map(PackageView::from).toList(), quote.total());
        }
    }

    record TariffReferenceView(UUID versionUuid, String manualCode, String versionLabel, String unit,
                               BigDecimal unitValue, BigDecimal factor) {

        static TariffReferenceView from(TariffManualVersion version, BigDecimal factor) {
            if (version == null) {
                return null;
            }
            return new TariffReferenceView(version.uuid(), version.manual().code(), version.label(),
                    version.manual().unit().name(), version.unitValue(), factor);
        }
    }

    record ServiceView(String cupsCode, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, PriceOrigin origin,
                       String originLabel, boolean billablePerService, String description, UUID referenceUuid,
                       String referenceCode) {

        static ServiceView from(PricedService service) {
            return new ServiceView(service.cupsCode(), service.quantity(), service.unitPrice(), service.lineTotal(),
                    service.origin(), service.origin().label(), service.origin().billablePerService(),
                    service.description(), service.referenceUuid(), service.referenceCode());
        }
    }

    record PackageView(UUID uuid, String code, String name, BigDecimal price) {

        static PackageView from(PricingQueries.AppliedPackage applied) {
            return new PackageView(applied.uuid(), applied.code(), applied.name(), applied.price());
        }
    }
}
