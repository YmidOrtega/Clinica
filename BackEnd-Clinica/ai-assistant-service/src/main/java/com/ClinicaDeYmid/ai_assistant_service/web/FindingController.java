package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.ai_assistant_service.service.FindingService;
import com.ClinicaDeYmid.ai_assistant_service.service.FindingViews;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Tag(name = "Hallazgos", description = "Bandeja de facturas emitidas con problemas, detectados por reglas")
class FindingController {

    static final String FINDINGS = "/api/v1/assistant/findings";

    private final FindingService findings;

    FindingController(FindingService findings) {
        this.findings = findings;
    }

    @GetMapping(FINDINGS)
    @PreAuthorize(Access.USE)
    @Operation(summary = "Bandeja de hallazgos",
            description = "Por gravedad (alta primero) y luego por fecha límite. Por defecto solo los abiertos")
    FindingViews.PageOf<FindingViews.FindingView> tray(
            @RequestParam(defaultValue = "OPEN") FindingStatus status,
            @RequestParam(required = false) FindingRule.Severity severity,
            @RequestParam(required = false) FindingRule rule,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z0-9]{1,24}$") String invoiceNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return findings.tray(status, severity, rule, invoiceNumber, page, size);
    }

    @GetMapping(FINDINGS + "/summary")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Cuántos hallazgos abiertos hay por regla y gravedad")
    FindingViews.Summary summary() {
        return findings.summary();
    }

    @GetMapping("/api/v1/assistant/invoices/{number}/findings")
    @PreAuthorize(Access.USE)
    @Operation(summary = "Historial de hallazgos de una factura, abiertos y resueltos")
    List<FindingViews.FindingView> ofInvoice(@PathVariable @Pattern(regexp = "^[A-Z0-9]{1,24}$") String number) {
        return findings.ofInvoice(number);
    }
}
