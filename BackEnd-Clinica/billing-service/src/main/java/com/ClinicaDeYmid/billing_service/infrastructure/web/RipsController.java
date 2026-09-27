package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.rips.MinistryValidation;
import com.ClinicaDeYmid.billing_service.application.rips.RipsDocument;
import com.ClinicaDeYmid.billing_service.application.rips.RipsDraft;
import com.ClinicaDeYmid.billing_service.application.rips.RipsQueries;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.MinistryFinding;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "RIPS", description = "Registro individual de prestación de servicios que soporta la factura (Res. 948 de 2026)")
class RipsController {

    private final RipsQueries rips;
    private final MinistryValidation validation;

    RipsController(RipsQueries rips, MinistryValidation validation) {
        this.rips = rips;
        this.validation = validation;
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

    @PostMapping(InvoiceController.INVOICES + "/{uuid}/rips-validation")
    @PreAuthorize(Access.FILE)
    @Operation(summary = "Validar el RIPS y la factura ante el mecanismo único de validación del Ministerio",
            description = "Exige el RIPS completo y la factura aceptada por la DIAN; envía el RIPS JSON y el "
                    + "AttachedDocument y guarda el CUV o los rechazos. Si el Ministerio no responde el envío queda "
                    + "pendiente y se reintenta solo; tras un rechazo, corregir y volver a pedirlo crea un envío nuevo")
    SubmissionView validate(@PathVariable UUID uuid) {
        return SubmissionView.from(validation.submit(uuid));
    }

    @GetMapping(InvoiceController.INVOICES + "/{uuid}/rips-validations")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Envíos del RIPS de una factura al Ministerio, del más reciente al más antiguo")
    List<SubmissionView> validations(@PathVariable UUID uuid) {
        return validation.ofInvoice(uuid).stream().map(SubmissionView::from).toList();
    }

    @GetMapping(value = InvoiceController.INVOICES + "/{uuid}/rips-validations/{submission}/{file}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Descargar el RIPS enviado (rips) o la respuesta del Ministerio (response) de un envío",
            description = "La respuesta con el CUV es la que se conserva y se entrega en la radicación")
    ResponseEntity<String> file(@PathVariable UUID uuid, @PathVariable UUID submission, @PathVariable String file) {
        RipsSubmission found = validation.ofInvoice(uuid).stream()
                .filter(candidate -> candidate.uuid().equals(submission)).findFirst()
                .orElseThrow(BillingException.InvoiceNotFound::new);
        String content = switch (file) {
            case "rips" -> found.rips();
            case "response" -> found.response();
            default -> null;
        };
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(content);
    }

    record SubmissionView(UUID uuid, int sequence, RipsSubmission.Status status, String cuv, Long processId,
                          Instant filedAt, boolean recovered, int attempts, Instant lastAttemptAt, String lastFailure,
                          Instant resolvedAt, Instant createdAt, String createdBy, List<FindingView> findings) {

        static SubmissionView from(RipsSubmission submission) {
            return new SubmissionView(submission.uuid(), submission.sequence(), submission.status(),
                    submission.cuv(), submission.processId(), submission.filedAt(), submission.recovered(),
                    submission.attempts(), submission.lastAttemptAt(), submission.lastFailure(),
                    submission.resolvedAt(), submission.createdAt(), submission.createdBy(),
                    submission.findings().stream().map(FindingView::from).toList());
        }
    }

    record FindingView(String kind, String code, String description, String observations, String path,
                       String source) {

        static FindingView from(MinistryFinding finding) {
            return new FindingView(finding.kind(), finding.code(), finding.description(), finding.observations(),
                    finding.path(), finding.source());
        }
    }

    record RipsView(boolean complete, List<String> gaps, RipsDocument rips) {
    }
}
