package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.rips.RipsDocument;
import com.ClinicaDeYmid.billing_service.application.rips.RipsDraft;
import com.ClinicaDeYmid.billing_service.application.rips.RipsQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "RIPS", description = "Registro individual de prestación de servicios que soporta la factura (Res. 948 de 2026)")
class RipsController {

    private final RipsQueries rips;

    RipsController(RipsQueries rips) {
        this.rips = rips;
    }

    @GetMapping(InvoiceController.INVOICES + "/{uuid}/rips")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Vista previa del RIPS JSON de una factura emitida",
            description = "Se arma con la factura, admisiones, pacientes, profesionales y los hechos de historia clínica; "
                    + "gaps lista lo que falta para que el mecanismo de validación del Ministerio lo acepte")
    RipsView draft(@PathVariable UUID uuid) {
        RipsDraft draft = rips.draft(uuid);
        return new RipsView(draft.complete(), draft.gaps(), draft.document());
    }

    record RipsView(boolean complete, List<String> gaps, RipsDocument rips) {
    }
}
