package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.InvoiceCommands;
import com.ClinicaDeYmid.billing_service.application.InvoiceQueries;
import com.ClinicaDeYmid.billing_service.application.DocumentAttachment;
import com.ClinicaDeYmid.billing_service.application.DocumentSigning;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.CoveragePlan;
import com.ClinicaDeYmid.billing_service.domain.DianStatus;
import com.ClinicaDeYmid.billing_service.domain.DianVerdict;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.HealthTerms;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.billing_service.domain.UncontractedCare;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Facturas", description = "Factura por unidad facturable: borrador y emisión con el consecutivo DIAN")
class InvoiceController {

    static final String INVOICES = "/api/v1/billing/invoices";

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceCommands commands;
    private final InvoiceQueries queries;
    private final DocumentSigning signing;
    private final DianDelivery delivery;
    private final DocumentAttachment attachment;
    private final RecentAuthentication recentAuthentication;

    InvoiceController(InvoiceCommands commands, InvoiceQueries queries, DocumentSigning signing, DianDelivery delivery,
                      DocumentAttachment attachment, RecentAuthentication recentAuthentication) {
        this.commands = commands;
        this.queries = queries;
        this.signing = signing;
        this.delivery = delivery;
        this.attachment = attachment;
        this.recentAuthentication = recentAuthentication;
    }

    @PostMapping(INVOICES)
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Preparar el borrador de factura de una unidad lista",
            description = "Ambulatorio: indica la venta. Urgencias y hospitalización: la cuenta completa tras el egreso. "
                    + "Se factura al pagador con el copago como pago compartido; al paciente si es particular. "
                    + "Facturar al pagador sin contrato exige el motivo, la cobertura, una justificación y un "
                    + "segundo factor reciente; la póliza es obligatoria en coberturas SOAT y planes voluntarios")
    ResponseEntity<InvoiceView> draft(@Valid @RequestBody Drafting request) {
        InvoiceCommands.Uncontracted uncontracted = null;
        if (request.uncontracted() != null) {
            AuthenticatedUser user = recentAuthentication.require();
            log.info("Step-up accepted to invoice {} to its payer without a contract ({}): {} authenticated at {}",
                    request.admissionNumber(), request.uncontracted().reason(), user.uuid(), user.authenticatedAt());
            uncontracted = request.uncontracted().toCommand();
        }
        Invoice invoice = commands.draft(request.admissionNumber(), request.saleUuid(), uncontracted,
                request.policyNumber());
        Invoice drafted = queries.invoice(invoice.uuid());
        return ResponseEntity.created(URI.create(INVOICES + "/" + drafted.uuid()))
                .eTag(EntityTags.of(drafted.version())).body(InvoiceView.from(drafted, null));
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
        List<Invoice> invoices = queries.ofAccount(admissionNumber);
        Map<UUID, ElectronicDocument> documents = queries.electronicDocuments(invoices);
        return invoices.stream().map(invoice -> InvoiceView.from(invoice, documents.get(invoice.uuid()))).toList();
    }

    @PostMapping(INVOICES + "/{uuid}/issuance")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Emitir la factura con el siguiente consecutivo de la resolución activa",
            description = "Exige un segundo factor reciente; falla si la cuenta cambió desde el borrador. "
                    + "Intenta firmarla de inmediato; si el custodio de la firma no responde queda emitida sin firma "
                    + "y se reintenta sola")
    ResponseEntity<InvoiceView> issue(@PathVariable UUID uuid,
                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = EntityTags.requiredVersion(ifMatch);
        AuthenticatedUser user = recentAuthentication.require();
        log.info("Step-up accepted to issue invoice {}: {} authenticated at {}", uuid, user.uuid(),
                user.authenticatedAt());
        ElectronicDocument issued = commands.issue(uuid, version);
        signing.trySign(issued.uuid());
        return tagged(uuid);
    }

    @PostMapping(INVOICES + "/{uuid}/signature")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Firmar con XAdES-EPES una factura emitida que quedó sin firma",
            description = "Idempotente: si ya está firmada la devuelve igual")
    ResponseEntity<InvoiceView> sign(@PathVariable UUID uuid) {
        signing.sign(queries.requireElectronicDocument(uuid).uuid());
        return tagged(uuid);
    }

    @PostMapping(INVOICES + "/{uuid}/dian-delivery")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Enviar ya a la DIAN una factura firmada, o reenviar una rechazada",
            description = "Las firmadas se envían solas en segundo plano; un rechazo por reglas no se reintenta solo y "
                    + "se reenvía aquí conservando el número. Si la DIAN no responde, queda en cola")
    ResponseEntity<InvoiceView> deliver(@PathVariable UUID uuid) {
        delivery.sendNow(queries.requireElectronicDocument(uuid).uuid());
        return tagged(uuid);
    }

    @GetMapping(INVOICES + "/{uuid}/dian-verdicts")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Respuestas de la DIAN a los envíos y consultas de una factura")
    List<VerdictView> verdicts(@PathVariable UUID uuid) {
        return queries.electronicDocument(uuid).map(document -> delivery.verdicts(document.uuid())).orElse(List.of())
                .stream().map(VerdictView::from).toList();
    }

    @GetMapping(value = INVOICES + "/{uuid}/attached-document", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el AttachedDocument firmado de la factura aceptada por la DIAN",
            description = "Contiene el UBL firmado y el ApplicationResponse de la DIAN; es lo que se entrega al "
                    + "adquiriente y al Ministerio de Salud. Si aún no existe se genera al pedirlo")
    ResponseEntity<String> attachedDocument(@PathVariable UUID uuid) {
        DocumentFile file = attachment.attach(queries.requireElectronicDocument(uuid).uuid());
        return ResponseEntity.ok().eTag("\"" + file.sha256() + "\"").contentType(MediaType.APPLICATION_XML)
                .body(file.content());
    }

    @GetMapping(value = INVOICES + "/{uuid}/ubl", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el XML UBL 2.1 de una factura emitida",
            description = "Firmado con XAdES-EPES si ya se firmó (signedAt en la factura); si no, el documento sin firma")
    ResponseEntity<String> ubl(@PathVariable UUID uuid) {
        DocumentFile document = queries.signedOrUnsigned(uuid);
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
        return ResponseEntity.ok().eTag(EntityTags.of(invoice.version()))
                .body(InvoiceView.from(invoice, queries.electronicDocument(uuid).orElse(null)));
    }

    record Drafting(@NotBlank @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber, UUID saleUuid,
                    @Valid UncontractedRequest uncontracted, @Size(max = 30) String policyNumber) {
    }

    record UncontractedRequest(@NotNull UncontractedCare reason, @NotNull CoveragePlan coverage,
                               @NotBlank @Size(max = 500) String justification) {

        InvoiceCommands.Uncontracted toCommand() {
            return new InvoiceCommands.Uncontracted(reason, coverage, justification);
        }
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

    record VerdictView(DianVerdict.Operation operation, DianVerdict.Outcome outcome, String fileName,
                       String trackId, String statusCode, String statusDescription, List<String> errors,
                       Instant receivedAt) {

        static VerdictView from(DianVerdict verdict) {
            return new VerdictView(verdict.operation(), verdict.outcome(), verdict.fileName(), verdict.trackId(),
                    verdict.statusCode(), verdict.statusDescription(), verdict.errors(), verdict.receivedAt());
        }
    }

    record DianView(DianStatus status, Instant since, String fileName, String trackId, int attempts) {

        static DianView from(ElectronicDocument document) {
            return document == null ? null : new DianView(document.dianStatus(), document.dianStatusAt(),
                    document.dianFileName(), document.dianTrackId(), document.dianAttempts());
        }
    }

    record StatusView(InvoiceStatus.Code code, String reason, Instant since) {

        static StatusView from(InvoiceStatus status) {
            return switch (status) {
                case InvoiceStatus.Draft ignored -> new StatusView(InvoiceStatus.Code.DRAFT, null, null);
                case InvoiceStatus.Issued issued -> new StatusView(InvoiceStatus.Code.ISSUED, null, issued.at());
                case InvoiceStatus.Discarded discarded ->
                        new StatusView(InvoiceStatus.Code.DISCARDED, discarded.reason(), discarded.at());
                case InvoiceStatus.Voided voided ->
                        new StatusView(InvoiceStatus.Code.VOIDED, voided.reason(), voided.at());
            };
        }
    }

    record SharedView(UUID invoiceUuid, String number, com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind kind,
                      BigDecimal amount) {

        static SharedView from(Invoice shared) {
            return new SharedView(shared.uuid(), shared.number(), shared.sharedPaymentKind(), shared.grossTotal());
        }
    }

    record HealthTermsView(String paymentModality, CoveragePlan coverage, String coverageCode, String cucon,
                           UncontractedCare uncontracted, String uncontractedCode, String uncontractedJustification,
                           String policyNumber) {

        static HealthTermsView from(Invoice invoice) {
            HealthTerms terms = invoice.healthTerms();
            if (terms == null) {
                return null;
            }
            return new HealthTermsView(terms.modality().sisproCode(), terms.coverage(), terms.coverage().sisproCode(),
                    terms.cucon(), terms.uncontracted(),
                    terms.uncontracted() == null ? null : terms.uncontracted().sisproCode(),
                    invoice.uncontractedJustification(), terms.policyNumber());
        }
    }

    record InvoiceView(UUID uuid, Invoice.Purpose purpose,
                       com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind sharedPaymentKind,
                       String authorizationNumber, String number, LocalDate issuedOn, String issuedTime, String cufe, String qrContent,
                       Instant signedAt, DianView dian, UUID resolutionUuid, StatusView status,
                       String admissionNumber, AccountSummary.UnitKind unitKind, UUID saleUuid, Buyer buyer,
                       HealthUser user, UUID contractUuid, String contractNumber, HealthTermsView health,
                       BigDecimal grossTotal,
                       BigDecimal patientShare, AccountSummary.ShareSource patientShareSource, BigDecimal payableTotal,
                       BigDecimal expectedShare, BigDecimal shareShortfall, List<SharedView> sharedPayments,
                       BigDecimal creditedTotal, List<LineView> lines, Instant createdAt) {

        static InvoiceView from(Invoice invoice, ElectronicDocument document) {
            return new InvoiceView(invoice.uuid(), invoice.purpose(), invoice.sharedPaymentKind(),
                    invoice.authorizationNumber(), invoice.number(), invoice.issuedOn(),
                    invoice.issuedTime() == null ? null : com.ClinicaDeYmid.billing_service.domain.Cufe.time(invoice.issuedTime()),
                    invoice.cufe(), invoice.qrContent(), document == null ? null : document.signedAt(), DianView.from(document),
                    invoice.resolutionUuid(),
                    StatusView.from(invoice.status()), invoice.account().admissionNumber(), invoice.unitKind(),
                    invoice.saleUuid(), invoice.buyer(), invoice.user(), invoice.contractUuid(),
                    invoice.contractNumber(), HealthTermsView.from(invoice), invoice.grossTotal(), invoice.patientShare(),
                    invoice.patientShareSource(), invoice.payableTotal(), invoice.expectedShare(),
                    invoice.shareShortfall(), invoice.sharedPayments().stream().map(SharedView::from).toList(),
                    invoice.creditedTotal(),
                    invoice.lines().stream().map(LineView::from).toList(), invoice.createdAt());
        }
    }
}
