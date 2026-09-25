package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.sale.FeeQueries;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFee;
import com.ClinicaDeYmid.billing_service.domain.SurgicalRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Honorarios", description = "Lo que la clínica debe a cada profesional por los procedimientos facturados")
class FeeController {

    private final FeeQueries queries;

    FeeController(FeeQueries queries) {
        this.queries = queries;
    }

    @GetMapping(SaleController.SALES + "/{uuid}/practitioner-fees")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Honorarios que genera una venta quirúrgica confirmada")
    List<FeeView> ofSale(@PathVariable UUID uuid) {
        return queries.ofSale(uuid).stream().map(FeeView::from).toList();
    }

    @GetMapping("/api/v1/billing/practitioner-fees")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Honorarios de un profesional, del más reciente al más antiguo",
            description = "UNAGREED son los procedimientos que no tienen un acuerdo por procedimiento que los cubra")
    List<FeeView> ofPractitioner(@RequestParam UUID practitionerUuid,
                                 @RequestParam(required = false) PractitionerFee.Status status,
                                 @RequestParam(defaultValue = "50") int limit) {
        return queries.ofPractitioner(practitionerUuid, status, limit).stream().map(FeeView::from).toList();
    }

    record FeeView(UUID uuid, String saleNumber, String admissionNumber, UUID saleLineUuid, String cupsCode,
                   String description, SurgicalRole role, UUID practitionerUuid, String practitionerName,
                   LocalDate performedOn, UUID agreementUuid, BigDecimal amount, PractitionerFee.Status status,
                   String statusReason) {

        static FeeView from(PractitionerFee fee) {
            return new FeeView(fee.uuid(), fee.sale().number(), fee.sale().account().admissionNumber(),
                    fee.saleLineUuid(), fee.cupsCode(), fee.description(), fee.role(), fee.practitionerUuid(),
                    fee.practitionerName(), fee.performedOn(), fee.agreementUuid(), fee.amount(), fee.status(),
                    fee.statusReason());
        }
    }
}
