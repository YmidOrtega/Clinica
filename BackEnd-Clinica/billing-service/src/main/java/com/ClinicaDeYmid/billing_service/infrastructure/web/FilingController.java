package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.RepresentationService;
import com.ClinicaDeYmid.billing_service.application.filing.FilingPackages;
import com.ClinicaDeYmid.billing_service.application.filing.FilingStatus;
import com.ClinicaDeYmid.billing_service.application.filing.InvoiceFilingService;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Radicación", description = "Radicación de facturas ante el pagador (Res. 2284 de 2023): paquete, radicado y plazo")
class FilingController {

    static final String PENDING = "/api/v1/billing/filings/pending";

    private final InvoiceFilingService filings;
    private final FilingPackages packages;
    private final CurrentUser currentUser = new CurrentUser();

    FilingController(InvoiceFilingService filings, FilingPackages packages) {
        this.filings = filings;
        this.packages = packages;
    }

    @PostMapping(InvoiceController.INVOICES + "/{uuid}/filing")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Registrar el radicado de la factura ante el pagador",
            description = "Exige el CUV del Ministerio. La fecha no puede ser futura ni anterior a la validación del RIPS; "
                    + "si supera el plazo de 22 días hábiles queda registrada como tardía")
    ResponseEntity<FilingView> register(@PathVariable UUID uuid, @Valid @RequestBody Registration request) {
        InvoiceFiling filing = filings.register(uuid, request.filingNumber(), request.filedOn());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(filing.version()))
                .body(FilingView.from(filings.status(uuid)));
    }

    @PutMapping(InvoiceController.INVOICES + "/{uuid}/filing")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Corregir el número o la fecha del radicado con motivo", description = "Exige If-Match; queda auditado")
    ResponseEntity<FilingView> correct(@PathVariable UUID uuid, @Valid @RequestBody Correction request,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        InvoiceFiling filing = filings.correct(uuid, EntityTags.requiredVersion(ifMatch), request.filingNumber(),
                request.filedOn(), request.reason());
        return ResponseEntity.ok().eTag(EntityTags.of(filing.version())).body(FilingView.from(filings.status(uuid)));
    }

    @GetMapping(InvoiceController.INVOICES + "/{uuid}/filing")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Radicado de la factura, o su plazo y semáforo si aún no se radica")
    ResponseEntity<FilingView> filing(@PathVariable UUID uuid) {
        FilingStatus status = filings.status(uuid);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (status.filing() != null) {
            response.eTag(EntityTags.of(status.filing().version()));
        }
        return response.body(FilingView.from(status));
    }

    @GetMapping(value = InvoiceController.INVOICES + "/{uuid}/filing-package", produces = "application/zip")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el paquete para radicar ante el pagador",
            description = "ZIP con el AttachedDocument, la representación gráfica sellada, el RIPS validado y la respuesta "
                    + "del Ministerio con el CUV")
    ResponseEntity<byte[]> filingPackage(@PathVariable UUID uuid) {
        AuthenticatedUser user = currentUser.get().orElseThrow();
        FilingPackages.Package built = packages.of(uuid,
                new RepresentationService.Requester(user.uuid(), user.role(), user.name()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"radicacion-" + built.number() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(built.zip());
    }

    @GetMapping(PENDING)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Bandeja de facturas por radicar con su plazo y semáforo",
            description = "Facturas de servicios emitidas al pagador sin radicado, de la más antigua a la más reciente; "
                    + "state filtra ON_TIME, DUE_SOON u OVERDUE")
    List<FilingView> pending(@RequestParam(required = false) UUID payerUuid,
                             @RequestParam(required = false) BusinessDeadline.State state,
                             @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        return filings.tray(payerUuid, state, limit).stream().map(FilingView::from).toList();
    }

    record Registration(@NotBlank @Size(max = 60) String filingNumber, @NotNull LocalDate filedOn) {
    }

    record Correction(@NotBlank @Size(max = 60) String filingNumber, @NotNull LocalDate filedOn,
                      @NotBlank @Size(max = 500) String reason) {
    }

    record FilingView(UUID invoiceUuid, String invoiceNumber, String admissionNumber, UUID payerUuid, String payerName,
                      BigDecimal payableTotal, LocalDate issuedOn, String cuv, boolean filed, String filingNumber,
                      LocalDate filedOn, LocalDate deadline, Boolean late, Integer remainingBusinessDays,
                      BusinessDeadline.State state, String correctionReason, Instant registeredAt, String registeredBy) {

        static FilingView from(FilingStatus status) {
            Invoice invoice = status.invoice();
            InvoiceFiling filing = status.filing();
            BusinessDeadline deadline = status.deadline();
            return new FilingView(invoice.uuid(), invoice.number(), invoice.account().admissionNumber(),
                    invoice.buyer().reference(), invoice.buyer().name(), invoice.payableTotal(), invoice.issuedOn(),
                    filing != null ? filing.cuv() : status.cuv(), filing != null,
                    filing == null ? null : filing.filingNumber(), filing == null ? null : filing.filedOn(),
                    filing != null ? filing.deadline() : deadline.deadline(), filing == null ? null : filing.late(),
                    deadline == null ? null : deadline.remainingBusinessDays(),
                    deadline == null ? null : deadline.state(), filing == null ? null : filing.correctionReason(),
                    filing == null ? null : filing.createdAt(), filing == null ? null : filing.createdBy());
        }
    }
}
