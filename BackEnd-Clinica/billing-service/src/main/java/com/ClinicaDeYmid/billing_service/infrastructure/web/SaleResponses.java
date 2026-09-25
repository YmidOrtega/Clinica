package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.LineOrigin;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import com.ClinicaDeYmid.billing_service.domain.SaleStatus;
import com.ClinicaDeYmid.billing_service.domain.SaleType;

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

    record LineView(UUID uuid, int position, UUID portfolioItemUuid, String cupsCode, String clinicCode,
                    String description, String category, int quantity, LocalDate serviceDate, OriginView origin,
                    boolean removed, Instant removedAt, String removalReason) {

        static LineView from(SaleLine line) {
            ChargedService service = line.service();
            return new LineView(line.uuid(), line.position(), service.portfolioItemUuid(), service.cupsCode(),
                    service.clinicCode(), service.description(), service.category(), line.quantity(),
                    line.serviceDate(), OriginView.from(line.origin()), line.removed(), line.removedAt(),
                    line.removalReason());
        }
    }

    record SaleView(UUID uuid, String number, String admissionNumber, SaleType.Code type, StatusView status,
                    List<LineView> lines, int activeLines, Instant createdAt, List<String> notes) {

        static SaleView from(Sale sale) {
            return from(sale, List.of());
        }

        static SaleView from(Sale sale, List<String> notes) {
            return new SaleView(sale.uuid(), sale.number(), sale.account().admissionNumber(), sale.type().code(),
                    StatusView.from(sale.status()), sale.lines().stream().map(LineView::from).toList(),
                    sale.activeLines().size(), sale.createdAt(), notes);
        }
    }

    private SaleResponses() {
    }
}
