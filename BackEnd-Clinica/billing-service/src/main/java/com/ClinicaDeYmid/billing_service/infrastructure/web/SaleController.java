package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.sale.OpenedSale;
import com.ClinicaDeYmid.billing_service.application.sale.SaleCommands;
import com.ClinicaDeYmid.billing_service.application.sale.SaleQueries;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import com.ClinicaDeYmid.billing_service.infrastructure.web.SaleResponses.SaleView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Ventas", description = "Ventas de servicios sobre el número de atención")
class SaleController {

    static final String SALES = "/api/v1/billing/sales";

    private final SaleCommands commands;
    private final SaleQueries queries;

    SaleController(SaleCommands commands, SaleQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping(SALES)
    @PreAuthorize(Access.SELL)
    @Operation(summary = "Abrir una venta sobre el número de atención",
            description = "Por defecto trae como líneas los servicios de las autorizaciones vigentes del episodio")
    ResponseEntity<SaleView> open(@Valid @RequestBody SaleRequests.Opening request) {
        OpenedSale opened = commands.open(request.admissionNumber(), SaleType.of(request.type()), request.preload());
        Sale sale = queries.sale(opened.sale().uuid());
        return ResponseEntity.created(URI.create(SALES + "/" + sale.uuid())).eTag(EntityTags.of(sale.version()))
                .body(SaleView.from(sale, opened.notes()));
    }

    @GetMapping(SALES + "/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una venta con sus líneas")
    ResponseEntity<SaleView> sale(@PathVariable UUID uuid) {
        return tagged(uuid);
    }

    @GetMapping(AccountController.BASE_PATH + "/{admissionNumber}/sales")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las ventas de un episodio")
    List<SaleView> salesOf(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        return queries.salesOf(admissionNumber).stream().map(SaleView::from).toList();
    }

    @PostMapping(SALES + "/{uuid}/lines")
    @PreAuthorize(Access.SELL)
    @Operation(summary = "Cargar un servicio del portafolio a la venta")
    ResponseEntity<SaleView> charge(@PathVariable UUID uuid,
                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @Valid @RequestBody SaleRequests.Line request) {
        long version = EntityTags.requiredVersion(ifMatch);
        commands.charge(uuid, version, request.toRequest());
        return tagged(uuid);
    }

    @PostMapping(SALES + "/{uuid}/lines/{lineUuid}/removal")
    @PreAuthorize(Access.SELL)
    @Operation(summary = "Retirar una línea de una venta en borrador, con motivo")
    ResponseEntity<SaleView> removeLine(@PathVariable UUID uuid, @PathVariable UUID lineUuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @Valid @RequestBody SaleRequests.Reason request) {
        commands.removeLine(uuid, EntityTags.requiredVersion(ifMatch), lineUuid, request.reason());
        return tagged(uuid);
    }

    @PostMapping(SALES + "/{uuid}/confirmation")
    @PreAuthorize(Access.SELL)
    @Operation(summary = "Confirmar la venta; sus líneas ya no cambian")
    ResponseEntity<SaleView> confirm(@PathVariable UUID uuid,
                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        commands.confirm(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(uuid);
    }

    @PostMapping(SALES + "/{uuid}/cancellation")
    @PreAuthorize(Access.SELL)
    @Operation(summary = "Anular una venta con motivo")
    ResponseEntity<SaleView> cancel(@PathVariable UUID uuid,
                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @Valid @RequestBody SaleRequests.Reason request) {
        commands.cancel(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(uuid);
    }

    private ResponseEntity<SaleView> tagged(UUID uuid) {
        Sale sale = queries.sale(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(sale.version())).body(SaleView.from(sale));
    }
}
