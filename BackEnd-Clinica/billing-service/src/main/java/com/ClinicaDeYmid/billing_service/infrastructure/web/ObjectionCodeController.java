package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.ObjectionCatalog;
import com.ClinicaDeYmid.billing_service.domain.ObjectionCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Glosas", description = "Devoluciones, glosas y respuestas (Res. 2284 de 2023, anexo técnico 3)")
class ObjectionCodeController {

    private final ObjectionCatalog catalog;

    ObjectionCodeController(ObjectionCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/billing/objection-codes")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Códigos del manual único de devoluciones, glosas y respuestas",
            description = "Solo los códigos de aplicación (6 caracteres) se registran; los de 4 agrupan")
    List<ObjectionCode> codes(@RequestParam(required = false) ObjectionCode.Kind kind) {
        return catalog.list(kind);
    }
}
