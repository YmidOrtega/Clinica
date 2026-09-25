package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.context.SaleContexts;
import com.ClinicaDeYmid.billing_service.infrastructure.web.SaleContextResponses.SaleContextView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(SaleContextController.BASE_PATH)
@Tag(name = "Ventas", description = "Todo lo que se necesita saber de un episodio antes de venderle servicios")
class SaleContextController {

    static final String BASE_PATH = "/api/v1/billing/sales";

    private final SaleContexts contexts;

    SaleContextController(SaleContexts contexts) {
        this.contexts = contexts;
    }

    @GetMapping("/context/{admissionNumber}")
    @PreAuthorize(Access.PREPARE_SALE)
    @Operation(summary = "Traer el contexto de la venta por número de atención",
            description = "Paciente, fase, pagador, contrato y autorizaciones vigentes con su copago. Sin admisiones "
                    + "responde 503; si faltan el paciente o el pagador, responde con complete=false y avisos")
    SaleContextView context(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        return SaleContextView.from(contexts.forAdmission(admissionNumber));
    }
}
