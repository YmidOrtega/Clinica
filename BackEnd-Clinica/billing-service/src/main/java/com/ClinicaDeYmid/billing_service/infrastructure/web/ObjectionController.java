package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.DocumentSigning;
import com.ClinicaDeYmid.billing_service.application.objection.ObjectionStatus;
import com.ClinicaDeYmid.billing_service.application.objection.PayerObjectionService;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.ObjectionItem;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@Tag(name = "Glosas", description = "Devoluciones, glosas y respuestas (Res. 2284 de 2023, anexo técnico 3)")
class ObjectionController {

    static final String OBJECTIONS = "/api/v1/billing/objections";

    private static final String VOID_AUTHORITY = "billing:void";

    private static final Logger log = LoggerFactory.getLogger(ObjectionController.class);

    private final PayerObjectionService objections;
    private final DocumentSigning signing;
    private final RecentAuthentication recentAuthentication;

    ObjectionController(PayerObjectionService objections, DocumentSigning signing,
                        RecentAuthentication recentAuthentication) {
        this.objections = objections;
        this.signing = signing;
        this.recentAuthentication = recentAuthentication;
    }

    @PostMapping(InvoiceController.INVOICES + "/{uuid}/objections")
    @PreAuthorize(Access.GLOSSES)
    @Operation(summary = "Registrar la devolución o las glosas que comunicó el pagador",
            description = "La factura debe estar radicada. Queda marcada como extemporánea si el pagador la comunicó "
                    + "después de 5 (devolución) o 20 (glosa) días hábiles desde la radicación")
    ResponseEntity<ObjectionView> register(@PathVariable UUID uuid, @Valid @RequestBody Registration request) {
        PayerObjection objection = objections.register(uuid, request.kind(), request.payerRecord(),
                request.notifiedOn(), request.items().stream().map(item -> new PayerObjection.Item(
                        item.invoiceLinePosition(), item.code(), item.amount(), item.detail())).toList());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(objection.version()))
                .body(ObjectionView.from(objections.status(objection.uuid())));
    }

    @GetMapping(InvoiceController.INVOICES + "/{uuid}/objections")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Devoluciones y glosas de una factura")
    List<ObjectionView> ofInvoice(@PathVariable UUID uuid) {
        return objections.ofInvoice(uuid).stream().map(ObjectionView::from).toList();
    }

    @GetMapping(OBJECTIONS + "/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Una devolución o glosa con sus causales, respuesta y decisión")
    ResponseEntity<ObjectionView> objection(@PathVariable UUID uuid) {
        ObjectionStatus status = objections.status(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(status.objection().version())).body(ObjectionView.from(status));
    }

    @PostMapping(OBJECTIONS + "/{uuid}/response")
    @PreAuthorize(Access.GLOSSES)
    @Operation(summary = "Responder la devolución o la glosa con los códigos RE del manual",
            description = "Exige If-Match y un segundo factor reciente. Si se acepta algún valor emite en la misma "
                    + "operación la nota crédito (parcial por línea para glosas, anulación para la devolución) y "
                    + "exige además billing:void")
    ResponseEntity<ObjectionView> respond(@PathVariable UUID uuid, @Valid @RequestBody Response request,
                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = EntityTags.requiredVersion(ifMatch);
        if (request.answers().stream().anyMatch(answer -> answer.acceptedAmount() != null
                && answer.acceptedAmount().signum() > 0)) {
            requireAuthority(VOID_AUTHORITY);
        }
        AuthenticatedUser user = recentAuthentication.require();
        log.info("Step-up accepted to answer objection {}: {} authenticated at {}", uuid, user.uuid(),
                user.authenticatedAt());
        PayerObjectionService.Responded responded = objections.respond(uuid, version, request.responseRecord(),
                request.respondedOn(), request.answers().stream().map(answer -> new PayerObjection.Answer(
                        answer.position(), answer.code(), answer.acceptedAmount(), answer.detail())).toList());
        if (responded.creditNote() != null) {
            signing.trySign(responded.creditNote().uuid());
        }
        return tagged(uuid);
    }

    @PostMapping(OBJECTIONS + "/{uuid}/decision")
    @PreAuthorize(Access.GLOSSES)
    @Operation(summary = "Registrar la decisión del pagador sobre la respuesta",
            description = "Por causal, el valor que el pagador deja en firme; lo que queda en firme va a conciliación. "
                    + "Exige If-Match y un segundo factor reciente")
    ResponseEntity<ObjectionView> decide(@PathVariable UUID uuid, @Valid @RequestBody Decision request,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        recentAuthentication.require();
        objections.decide(uuid, EntityTags.requiredVersion(ifMatch), request.decidedOn(),
                request.rulings().stream().map(ruling -> new PayerObjection.Ruling(ruling.position(),
                        ruling.upheldAmount())).toList());
        return tagged(uuid);
    }

    @GetMapping(OBJECTIONS + "/pending")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Bandeja de devoluciones y glosas por responder con su semáforo",
            description = "state filtra ON_TIME, DUE_SOON u OVERDUE; vencida significa aceptación tácita")
    List<ObjectionView> pending(@RequestParam(required = false) UUID payerUuid,
                                @RequestParam(required = false) BusinessDeadline.State state,
                                @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        return objections.tray(payerUuid, state, limit).stream().map(ObjectionView::from).toList();
    }

    private static void requireAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(granted -> authority.equals(granted.getAuthority()))) {
            throw new AccessDeniedException("Aceptar un valor emite una nota crédito y exige " + authority);
        }
    }

    private ResponseEntity<ObjectionView> tagged(UUID uuid) {
        ObjectionStatus status = objections.status(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(status.objection().version())).body(ObjectionView.from(status));
    }

    record Registration(@NotNull PayerObjection.Kind kind, @NotBlank @Size(max = 60) String payerRecord,
                        @NotNull LocalDate notifiedOn, @NotEmpty @Size(max = 500) List<@Valid ItemRequest> items) {
    }

    record ItemRequest(Integer invoiceLinePosition, @NotBlank @Size(max = 6) String code, BigDecimal amount,
                       @Size(max = 500) String detail) {
    }

    record Response(@NotBlank @Size(max = 60) String responseRecord, @NotNull LocalDate respondedOn,
                    @NotEmpty @Size(max = 500) List<@Valid AnswerRequest> answers) {
    }

    record AnswerRequest(int position, @NotBlank @Size(max = 6) String code, BigDecimal acceptedAmount,
                         @Size(max = 1000) String detail) {
    }

    record Decision(@NotNull LocalDate decidedOn, @NotEmpty @Size(max = 500) List<@Valid RulingRequest> rulings) {
    }

    record RulingRequest(int position, @NotNull BigDecimal upheldAmount) {
    }

    record ObjectionView(UUID uuid, UUID invoiceUuid, String invoiceNumber, String admissionNumber, UUID payerUuid,
                         String payerName, PayerObjection.Kind kind, PayerObjection.Status status, String payerRecord,
                         LocalDate notifiedOn, LocalDate formulationDeadline, boolean extemporaneous,
                         BigDecimal claimedAmount, LocalDate responseDeadline, Integer remainingBusinessDays,
                         BusinessDeadline.State state, String responseRecord, LocalDate respondedOn,
                         Boolean respondedLate, BigDecimal acceptedAmount, UUID creditNoteUuid, String creditNoteNumber,
                         LocalDate decisionDeadline, LocalDate decidedOn, BigDecimal upheldAmount,
                         PayerObjection.Outcome outcome, List<ItemView> items, Instant registeredAt,
                         String registeredBy) {

        static ObjectionView from(ObjectionStatus status) {
            PayerObjection objection = status.objection();
            BusinessDeadline due = status.responseDue();
            return new ObjectionView(objection.uuid(), objection.invoice().uuid(), objection.invoice().number(),
                    objection.invoice().account().admissionNumber(), objection.invoice().buyer().reference(),
                    objection.invoice().buyer().name(), objection.kind(), objection.status(), objection.payerRecord(),
                    objection.notifiedOn(), objection.formulationDeadline(), objection.extemporaneous(),
                    objection.claimedAmount(), objection.responseDeadline(),
                    due == null ? null : due.remainingBusinessDays(), due == null ? null : due.state(),
                    objection.responseRecord(), objection.respondedOn(),
                    objection.respondedOn() == null ? null : objection.respondedLate(), objection.acceptedAmount(),
                    objection.creditNote() == null ? null : objection.creditNote().uuid(),
                    objection.creditNote() == null ? null : objection.creditNote().number(),
                    objection.decisionDeadline(), objection.decidedOn(), objection.upheldAmount(), objection.outcome(),
                    objection.items().stream().map(ItemView::from).toList(), objection.createdAt(),
                    objection.createdBy());
        }
    }

    record ItemView(int position, Integer invoiceLinePosition, String code, BigDecimal amount, String detail,
                    String responseCode, BigDecimal acceptedAmount, String responseDetail, BigDecimal upheldAmount) {

        static ItemView from(ObjectionItem item) {
            return new ItemView(item.position(), item.invoiceLinePosition(), item.code(), item.amount(), item.detail(),
                    item.responseCode(), item.acceptedAmount(), item.responseDetail(), item.upheldAmount());
        }
    }
}
