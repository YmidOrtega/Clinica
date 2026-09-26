package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.CreditNoteCommands;
import com.ClinicaDeYmid.billing_service.application.CreditNoteQueries;
import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.DocumentAttachment;
import com.ClinicaDeYmid.billing_service.application.DocumentSigning;
import com.ClinicaDeYmid.billing_service.domain.CreditConcept;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNoteLine;
import com.ClinicaDeYmid.billing_service.domain.CreditRequest;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Notas crédito", description = "Anulación total o crédito parcial de una factura aceptada por la DIAN")
class CreditNoteController {

    static final String CREDIT_NOTES = "/api/v1/billing/credit-notes";

    private static final Logger log = LoggerFactory.getLogger(CreditNoteController.class);

    private final CreditNoteCommands commands;
    private final CreditNoteQueries queries;
    private final DocumentSigning signing;
    private final DianDelivery delivery;
    private final DocumentAttachment attachment;
    private final RecentAuthentication recentAuthentication;

    CreditNoteController(CreditNoteCommands commands, CreditNoteQueries queries, DocumentSigning signing,
                         DianDelivery delivery, DocumentAttachment attachment,
                         RecentAuthentication recentAuthentication) {
        this.commands = commands;
        this.queries = queries;
        this.signing = signing;
        this.delivery = delivery;
        this.attachment = attachment;
        this.recentAuthentication = recentAuthentication;
    }

    @PostMapping(InvoiceController.INVOICES + "/{invoiceUuid}/credit-notes")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Emitir una nota crédito sobre una factura aceptada por la DIAN",
            description = "VOID anula la factura completa y libera la unidad para refacturar; los demás conceptos "
                    + "acreditan líneas por cantidad o por valor sin superar lo facturado. Exige un segundo factor "
                    + "reciente y el ETag de la factura")
    ResponseEntity<CreditNoteView> issue(@PathVariable UUID invoiceUuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @Valid @RequestBody Crediting request) {
        long version = EntityTags.requiredVersion(ifMatch);
        AuthenticatedUser user = recentAuthentication.require();
        log.info("Step-up accepted to credit invoice {}: {} authenticated at {}", invoiceUuid, user.uuid(),
                user.authenticatedAt());
        ElectronicDocument issued = commands.issue(invoiceUuid, version, request.concept(), request.reason(),
                request.lines() == null ? List.of() : request.lines().stream().map(CreditLine::toDomain).toList());
        signing.trySign(issued.uuid());
        CreditNote note = issued.creditNote();
        return ResponseEntity.created(URI.create(CREDIT_NOTES + "/" + note.uuid())).body(view(note.uuid()));
    }

    @GetMapping(InvoiceController.INVOICES + "/{invoiceUuid}/credit-notes")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Notas crédito de una factura")
    List<CreditNoteView> ofInvoice(@PathVariable UUID invoiceUuid) {
        return queries.ofInvoice(invoiceUuid).stream().map(note -> view(note.uuid())).toList();
    }

    @GetMapping(CREDIT_NOTES + "/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una nota crédito con sus líneas y su estado ante la DIAN")
    CreditNoteView note(@PathVariable UUID uuid) {
        return view(uuid);
    }

    @GetMapping(value = CREDIT_NOTES + "/{uuid}/attached-document", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el AttachedDocument firmado de la nota crédito aceptada por la DIAN",
            description = "Contiene el UBL firmado y el ApplicationResponse de la DIAN; es lo que se entrega al "
                    + "adquiriente y al Ministerio de Salud. Si aún no existe se genera al pedirlo")
    ResponseEntity<String> attachedDocument(@PathVariable UUID uuid) {
        DocumentFile file = attachment.attach(queries.electronicDocument(uuid).uuid());
        return ResponseEntity.ok().eTag("\"" + file.sha256() + "\"").contentType(MediaType.APPLICATION_XML)
                .body(file.content());
    }

    @GetMapping(value = CREDIT_NOTES + "/{uuid}/ubl", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el XML UBL 2.1 de la nota, firmado si ya se firmó")
    ResponseEntity<String> ubl(@PathVariable UUID uuid) {
        DocumentFile file = queries.signedOrUnsigned(uuid);
        return ResponseEntity.ok().eTag("\"" + file.sha256() + "\"").contentType(MediaType.APPLICATION_XML)
                .body(file.content());
    }

    @PostMapping(CREDIT_NOTES + "/{uuid}/signature")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Firmar con XAdES-EPES una nota que quedó sin firma", description = "Idempotente")
    CreditNoteView sign(@PathVariable UUID uuid) {
        signing.sign(queries.electronicDocument(uuid).uuid());
        return view(uuid);
    }

    @PostMapping(CREDIT_NOTES + "/{uuid}/dian-delivery")
    @PreAuthorize(Access.INVOICE)
    @Operation(summary = "Enviar ya a la DIAN una nota firmada, o reenviar una rechazada")
    CreditNoteView deliver(@PathVariable UUID uuid) {
        delivery.sendNow(queries.electronicDocument(uuid).uuid());
        return view(uuid);
    }

    @GetMapping(CREDIT_NOTES + "/{uuid}/dian-verdicts")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Respuestas de la DIAN a los envíos y consultas de una nota")
    List<InvoiceController.VerdictView> verdicts(@PathVariable UUID uuid) {
        return delivery.verdicts(queries.electronicDocument(uuid).uuid()).stream()
                .map(InvoiceController.VerdictView::from).toList();
    }

    private CreditNoteView view(UUID uuid) {
        return CreditNoteView.from(queries.note(uuid), queries.electronicDocument(uuid));
    }

    record Crediting(@NotNull CreditConcept concept, @NotBlank @Size(max = 500) String reason,
                     @Valid List<CreditLine> lines) {
    }

    record CreditLine(@NotNull @Positive Integer invoiceLinePosition, @Positive Integer quantity, BigDecimal amount) {

        CreditRequest toDomain() {
            return new CreditRequest(invoiceLinePosition, quantity, amount);
        }
    }

    record LineView(int position, int invoiceLinePosition, String code, String description, int quantity,
                    BigDecimal unitPrice, BigDecimal lineTotal) {

        static LineView from(CreditNoteLine line) {
            return new LineView(line.position(), line.invoiceLinePosition(), line.code(), line.description(),
                    line.quantity(), line.unitPrice(), line.lineTotal());
        }
    }

    record CreditNoteView(UUID uuid, String number, UUID invoiceUuid, String invoiceNumber, CreditConcept concept,
                          String conceptDianCode, String reason, LocalDate issuedOn, String issuedTime, String cude,
                          BigDecimal creditedGross, BigDecimal creditedShare, BigDecimal creditedPayable,
                          List<LineView> lines, Instant signedAt, InvoiceController.DianView dian) {

        static CreditNoteView from(CreditNote note, ElectronicDocument document) {
            return new CreditNoteView(note.uuid(), note.number(), note.invoice().uuid(), note.invoice().number(),
                    note.concept(), note.concept().dianCode(), note.reason(), note.issuedOn(),
                    Cufe.time(note.issuedTime()), note.cude(), note.creditedGross(), note.creditedShare(),
                    note.creditedPayable(), note.lines().stream().map(LineView::from).toList(), document.signedAt(),
                    InvoiceController.DianView.from(document));
        }
    }
}
