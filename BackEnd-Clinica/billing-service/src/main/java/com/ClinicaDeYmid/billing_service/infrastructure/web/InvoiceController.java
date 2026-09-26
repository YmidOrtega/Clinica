package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.InvoiceCommands;
import com.ClinicaDeYmid.billing_service.application.InvoiceQueries;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Facturas", description = "Factura por unidad facturable: borrador y emisión con el consecutivo DIAN")
class InvoiceController {

    static final String INVOICES = "/api/v1/billing/invoices";

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceCommands commands;
    private final InvoiceQueries queries;
    private final RecentAuthentication recentAuthentication;

    InvoiceController(InvoiceCommands commands, InvoiceQueries queries, RecentAuthentication recentAuthentication) {
        this.commands = commands;
        this.queries = queries;
        this.recentAuthentication = recentAuthentication;
    }

    @PostMapping(INVOICES)
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Preparar el borrador de factura de una unidad lista",
            description = "Ambulatorio: indica la venta. Urgencias y hospitalización: la cuenta completa tras el egreso. "
                    + "Se factura al pagador con el copago como pago compartido; al paciente si es particular")
    ResponseEntity<InvoiceView> draft(@Valid @RequestBody Drafting request) {
        Invoice invoice = commands.draft(request.admissionNumber(), request.saleUuid());
        Invoice drafted = queries.invoice(invoice.uuid());
        return ResponseEntity.created(URI.create(INVOICES + "/" + drafted.uuid()))
                .eTag(EntityTags.of(drafted.version())).body(InvoiceView.from(drafted));
    }

    @GetMapping(INVOICES + "/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una factura con sus líneas")
    ResponseEntity<InvoiceView> invoice(@PathVariable UUID uuid) {
        return tagged(uuid);
    }

    @GetMapping(AccountController.BASE_PATH + "/{admissionNumber}/invoices")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Facturas de un episodio")
    List<InvoiceView> ofAccount(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        return queries.ofAccount(admissionNumber).stream().map(InvoiceView::from).toList();
    }

    @PostMapping(INVOICES + "/{uuid}/issuance")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Emitir la factura con el siguiente consecutivo de la resolución activa",
            description = "Exige un segundo factor reciente; falla si la cuenta cambió desde el borrador")
    ResponseEntity<InvoiceView> issue(@PathVariable UUID uuid,
                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = EntityTags.requiredVersion(ifMatch);
        AuthenticatedUser user = recentAuthentication.require();
        log.info("Step-up accepted to issue invoice {}: {} authenticated at {}", uuid, user.uuid(),
                user.authenticatedAt());
        commands.issue(uuid, version);
        return tagged(uuid);
    }

    @GetMapping(value = INVOICES + "/{uuid}/ubl", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el XML UBL 2.1 de una factura emitida, sin firma",
            description = "La firma XAdES llega al enviar a la DIAN; este es el documento exacto que se firmará")
    ResponseEntity<String> ubl(@PathVariable UUID uuid) {
        InvoiceDocument document = queries.document(uuid, InvoiceDocument.Kind.UBL_UNSIGNED);
        return ResponseEntity.ok().eTag("\"" + document.sha256() + "\"").contentType(MediaType.APPLICATION_XML)
                .body(document.content());
    }

    @PostMapping(INVOICES + "/{uuid}/discard")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Descartar un borrador de factura con motivo")
    ResponseEntity<InvoiceView> discard(@PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @Valid @RequestBody Reason request) {
        commands.discard(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(uuid);
    }

    private ResponseEntity<InvoiceView> tagged(UUID uuid) {
        Invoice invoice = queries.invoice(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(invoice.version())).body(InvoiceView.from(invoice));
    }

    record Drafting(@NotBlank @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber, UUID saleUuid) {
    }

    record Reason(@NotBlank String reason) {
    }

    record LineView(int position, InvoiceLine.Kind kind, String saleNumber, String code, String description,
                    int quantity, BigDecimal unitPrice, BigDecimal lineTotal, String priceOrigin, LocalDate serviceDate,
                    String authorizationNumber) {

        static LineView from(InvoiceLine line) {
            return new LineView(line.position(), line.kind(), line.saleNumber(), line.code(), line.description(),
                    line.quantity(), line.unitPrice(), line.lineTotal(), line.priceOrigin(), line.serviceDate(),
                    line.authorizationNumber());
        }
    }

    record StatusView(InvoiceStatus.Code code, String reason, Instant since) {

        static StatusView from(InvoiceStatus status) {
            return switch (status) {
                case InvoiceStatus.Draft ignored -> new StatusView(InvoiceStatus.Code.DRAFT, null, null);
                case InvoiceStatus.Issued issued -> new StatusView(InvoiceStatus.Code.ISSUED, null, issued.at());
                case InvoiceStatus.Discarded discarded ->
                        new StatusView(InvoiceStatus.Code.DISCARDED, discarded.reason(), discarded.at());
            };
        }
    }

    record InvoiceView(UUID uuid, String number, LocalDate issuedOn, String issuedTime, String cufe, String qrContent,
                       UUID resolutionUuid, StatusView status,
                       String admissionNumber, AccountSummary.UnitKind unitKind, UUID saleUuid, Buyer buyer,
                       HealthUser user, UUID contractUuid, String contractNumber, BigDecimal grossTotal,
                       BigDecimal patientShare, AccountSummary.ShareSource patientShareSource, BigDecimal payableTotal,
                       List<LineView> lines, Instant createdAt) {

        static InvoiceView from(Invoice invoice) {
            return new InvoiceView(invoice.uuid(), invoice.number(), invoice.issuedOn(),
                    invoice.issuedTime() == null ? null : com.ClinicaDeYmid.billing_service.domain.Cufe.time(invoice.issuedTime()),
                    invoice.cufe(), invoice.qrContent(), invoice.resolutionUuid(),
                    StatusView.from(invoice.status()), invoice.account().admissionNumber(), invoice.unitKind(),
                    invoice.saleUuid(), invoice.buyer(), invoice.user(), invoice.contractUuid(),
                    invoice.contractNumber(), invoice.grossTotal(), invoice.patientShare(),
                    invoice.patientShareSource(), invoice.payableTotal(),
                    invoice.lines().stream().map(LineView::from).toList(), invoice.createdAt());
        }
    }
}
