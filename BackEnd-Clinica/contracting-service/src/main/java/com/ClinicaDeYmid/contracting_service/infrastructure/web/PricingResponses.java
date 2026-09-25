package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PricingQueries;
import com.ClinicaDeYmid.contracting_service.domain.PriceOrigin;
import com.ClinicaDeYmid.contracting_service.domain.PricedService;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalBasis;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalComponent;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalLiquidation;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
                       String referenceCode, boolean authorizationRequired, boolean surgical) {

        static ServiceView from(PricedService service) {
            return new ServiceView(service.cupsCode(), service.quantity(), service.unitPrice(), service.lineTotal(),
                    service.origin(), service.origin().label(), service.origin().billablePerService(),
                    service.description(), service.referenceUuid(), service.referenceCode(),
                    service.authorizationRequired(), service.surgical());
        }
    }

    record PackageView(UUID uuid, String code, String name, BigDecimal price) {

        static PackageView from(PricingQueries.AppliedPackage applied) {
            return new PackageView(applied.uuid(), applied.code(), applied.name(), applied.price());
        }
    }

    record ComponentView(SurgicalComponent component, String label, BigDecimal fullValue, BigDecimal percent,
                         BigDecimal amount) {

        static ComponentView from(SurgicalLiquidation.ComponentCharge charge) {
            return new ComponentView(charge.component(), charge.component().label(), charge.fullValue(),
                    charge.percent(), charge.amount());
        }
    }

    record ProcedureView(String cupsCode, String route, String description, PriceOrigin origin, String originLabel,
                         boolean billablePerService, BigDecimal surgicalBasis, Integer order, boolean principal,
                         boolean sameRoute, List<ComponentView> components, BigDecimal total, UUID referenceUuid,
                         String referenceCode, boolean authorizationRequired) {

        static ProcedureView from(PricingQueries.QuotedProcedure procedure) {
            return new ProcedureView(procedure.cupsCode(), procedure.route(), procedure.description(),
                    procedure.origin(), procedure.origin().label(), procedure.origin().billablePerService(),
                    procedure.basis(), procedure.order(), procedure.principal(), procedure.sameRoute(),
                    procedure.components().stream().map(ComponentView::from).toList(), procedure.total(),
                    procedure.referenceUuid(), procedure.referenceCode(), procedure.authorizationRequired());
        }
    }

    record RulesReferenceView(UUID uuid, SurgicalBasis basis, String checksum) {
    }

    record SurgicalQuoteView(UUID contractUuid, String contractNumber, UUID payerUuid, String payerName, LocalDate on,
                             TariffReferenceView tariff, RulesReferenceView rules, List<ProcedureView> procedures,
                             List<PackageView> packages, Map<SurgicalComponent, BigDecimal> componentTotals,
                             BigDecimal total) {

        static SurgicalQuoteView from(PricingQueries.SurgicalQuote quote) {
            return new SurgicalQuoteView(quote.contract().uuid(), quote.contract().number(),
                    quote.contract().payer().uuid(), quote.contract().payer().socialReason(), quote.date(),
                    TariffReferenceView.from(quote.tariffVersion(), quote.contract().tariffFactor()),
                    quote.rules() == null ? null : new RulesReferenceView(quote.rules().uuid(), quote.rules().basis(),
                            quote.rules().checksum()),
                    quote.procedures().stream().map(ProcedureView::from).toList(),
                    quote.packages().stream().map(PackageView::from).toList(), quote.componentTotals(), quote.total());
        }
    }
}
