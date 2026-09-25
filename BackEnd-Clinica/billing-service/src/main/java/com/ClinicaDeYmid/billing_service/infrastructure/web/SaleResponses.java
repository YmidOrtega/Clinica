package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.AuthorizationCheck;
import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.LineOrigin;
import com.ClinicaDeYmid.billing_service.domain.LinePrice;
import com.ClinicaDeYmid.billing_service.domain.PackageCharge;
import com.ClinicaDeYmid.billing_service.domain.PriceOrigin;
import com.ClinicaDeYmid.billing_service.domain.PricedSale;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import com.ClinicaDeYmid.billing_service.domain.SaleStatus;
import com.ClinicaDeYmid.billing_service.domain.SaleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class SaleResponses {

    record StatusView(SaleStatus.Code code, String reason, Instant since) {

        static StatusView from(SaleStatus status) {
            return switch (status) {
                case SaleStatus.Draft ignored -> new StatusView(SaleStatus.Code.DRAFT, null, null);
                case SaleStatus.Confirmed confirmed -> new StatusView(SaleStatus.Code.CONFIRMED, null, confirmed.since());
                case SaleStatus.Cancelled cancelled ->
                        new StatusView(SaleStatus.Code.CANCELLED, cancelled.reason(), cancelled.since());
            };
        }
    }

    record OriginView(LineOrigin.Code code, UUID authorizationUuid, String authorizationNumber) {

        static OriginView from(LineOrigin origin) {
            return switch (origin) {
                case LineOrigin.Manual ignored -> new OriginView(LineOrigin.Code.MANUAL, null, null);
                case LineOrigin.Authorized authorized -> new OriginView(LineOrigin.Code.AUTHORIZED,
                        authorized.authorizationUuid(), authorized.authorizationNumber());
            };
        }
    }

    record PriceView(PriceOrigin origin, BigDecimal unitPrice, BigDecimal lineTotal, boolean billablePerService,
                     UUID referenceUuid, String referenceCode) {

        static PriceView from(LinePrice price) {
            return price == null ? null : new PriceView(price.origin(), price.unitPrice(), price.lineTotal(),
                    price.origin().billablePerService(), price.referenceUuid(), price.referenceCode());
        }
    }

    record ManualPriceView(BigDecimal unitPrice, String reason) {
    }

    record LineView(UUID uuid, int position, UUID portfolioItemUuid, String cupsCode, String clinicCode,
                    String description, String category, int quantity, LocalDate serviceDate, OriginView origin,
                    boolean removed, Instant removedAt, String removalReason, ManualPriceView manualPrice,
                    PriceView price, AuthorizationCheck authorization) {

        static LineView from(SaleLine line) {
            return from(line, line.price().orElse(null), null);
        }

        static LineView from(SaleLine line, LinePrice price, AuthorizationCheck authorization) {
            ChargedService service = line.service();
            return new LineView(line.uuid(), line.position(), service.portfolioItemUuid(), service.cupsCode(),
                    service.clinicCode(), service.description(), service.category(), line.quantity(),
                    line.serviceDate(), OriginView.from(line.origin()), line.removed(), line.removedAt(),
                    line.removalReason(), line.manualUnitPrice()
                    .map(unit -> new ManualPriceView(unit, line.manualPriceReason())).orElse(null),
                    PriceView.from(price), authorization);
        }
    }

    record SettlementView(UUID contractUuid, String contractNumber, UUID payerUuid, BigDecimal linesTotal,
                          BigDecimal packagesTotal, BigDecimal total, List<PackageCharge> packages) {

        static SettlementView from(Sale sale) {
            return sale.settlement().map(settled -> new SettlementView(settled.contractUuid(),
                    settled.contractNumber(), settled.payerUuid(), settled.linesTotal(), settled.packagesTotal(),
                    settled.total(), sale.packages().stream().map(applied -> applied.charge()).toList()))
                    .orElse(null);
        }
    }

    record PreviewView(UUID saleUuid, String number, UUID contractUuid, String contractNumber, boolean complete,
                       List<String> unpricedCups, List<String> unauthorizedCups, List<LineView> lines,
                       List<PackageCharge> packages, BigDecimal linesTotal, BigDecimal packagesTotal,
                       BigDecimal total) {

        static PreviewView from(Sale sale, PricedSale priced) {
            return new PreviewView(sale.uuid(), sale.number(), priced.terms().contractUuid(),
                    priced.terms().contractNumber(), priced.complete(),
                    priced.pending().stream().map(line -> line.service().cupsCode()).distinct().toList(),
                    priced.unauthorized().stream().map(line -> line.service().cupsCode()).distinct().toList(),
                    sale.activeLines().stream().map(line -> LineView.from(line, priced.lines().get(line.uuid()),
                            priced.authorizations().get(line.uuid()))).toList(),
                    priced.terms().packages(), priced.linesTotal(), priced.packagesTotal(), priced.total());
        }
    }

    record SaleView(UUID uuid, String number, String admissionNumber, SaleType.Code type, StatusView status,
                    List<LineView> lines, int activeLines, SettlementView settlement, Instant createdAt,
                    List<String> notes) {

        static SaleView from(Sale sale) {
            return from(sale, List.of());
        }

        static SaleView from(Sale sale, List<String> notes) {
            return new SaleView(sale.uuid(), sale.number(), sale.account().admissionNumber(), sale.type().code(),
                    StatusView.from(sale.status()), sale.lines().stream().map(LineView::from).toList(),
                    sale.activeLines().size(), SettlementView.from(sale), sale.createdAt(), notes);
        }
    }

    private SaleResponses() {
    }
}
