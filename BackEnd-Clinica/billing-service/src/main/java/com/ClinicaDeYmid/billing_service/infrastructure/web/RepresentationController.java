package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.RepresentationService;
import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentation;
import com.ClinicaDeYmid.commons.documents.DocumentSealer;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Representación gráfica",
        description = "PDF de facturas y notas crédito, sellado con la clave institucional y verificable")
class RepresentationController {

    static final String REPRESENTATIONS = "/api/v1/billing/graphic-representations";
    static final String PUBLIC_VERIFICATION = REPRESENTATIONS + "/verification";
    static final String SEAL_KEYS = "/api/v1/billing/seal-keys";

    private final RepresentationService representations;
    private final DocumentSealer sealer;
    private final CurrentUser currentUser = new CurrentUser();

    RepresentationController(RepresentationService representations, DocumentSealer sealer) {
        this.representations = representations;
        this.sealer = sealer;
    }

    @PostMapping(path = InvoiceController.INVOICES + "/{uuid}/graphic-representation",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Generar el PDF de una factura emitida",
            description = "Se genera en cada petición, se sella y se registra su huella SHA-256; el PDF no se guarda")
    ResponseEntity<byte[]> ofInvoice(@PathVariable UUID uuid) {
        return pdf(representations.ofInvoice(uuid, requester()));
    }

    @PostMapping(path = CreditNoteController.CREDIT_NOTES + "/{uuid}/graphic-representation",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Generar el PDF de una nota crédito",
            description = "Se genera en cada petición, se sella y se registra su huella SHA-256; el PDF no se guarda")
    ResponseEntity<byte[]> ofCreditNote(@PathVariable UUID uuid) {
        return pdf(representations.ofCreditNote(uuid, requester()));
    }

    @GetMapping(REPRESENTATIONS + "/{id}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar el registro y el sello de una representación gráfica")
    RepresentationView find(@PathVariable UUID id) {
        return RepresentationView.from(representations.find(id));
    }

    @PostMapping(path = REPRESENTATIONS + "/{id}/verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Verificar que un PDF es exactamente el generado y que su sello es válido")
    VerificationView verify(@PathVariable UUID id, @RequestPart("document") MultipartFile document) {
        RepresentationService.Verified verified = representations.verify(id, bytesOf(document));
        return new VerificationView(RepresentationView.from(verified.representation()),
                verified.verification().documentMatches(), verified.verification().sealValid(),
                verified.verification().authentic());
    }

    @PostMapping(PUBLIC_VERIFICATION)
    @Operation(summary = "Comprobar públicamente si una huella corresponde a un PDF auténtico",
            description = "No exige credenciales y no revela ningún dato del documento, del adquiriente ni del paciente")
    PublicVerificationView verifyPublicly(@Valid @RequestBody PublicCheck check) {
        return new PublicVerificationView(representations.authentic(check.number(), check.sha256()));
    }

    @GetMapping(SEAL_KEYS)
    @Operation(summary = "Publicar las claves públicas con las que se sellan las representaciones gráficas")
    Map<String, String> sealKeys() {
        return sealer.publicKeysPem();
    }

    private RepresentationService.Requester requester() {
        AuthenticatedUser user = currentUser.get().orElseThrow();
        return new RepresentationService.Requester(user.uuid(), user.role(), user.name());
    }

    private static ResponseEntity<byte[]> pdf(RepresentationService.Rendered rendered) {
        GraphicRepresentation representation = rendered.representation();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + representation.documentNumber() + ".pdf\"")
                .header("X-Representation-Id", representation.id().toString())
                .header("X-Document-Sha256", representation.sha256())
                .body(rendered.pdf());
    }

    private static byte[] bytesOf(MultipartFile document) {
        try {
            return document.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    record PublicCheck(@NotBlank String number,
                       @Pattern(regexp = "^[0-9a-f]{64}$", message = "debe ser una huella SHA-256 en hexadecimal")
                       String sha256) {
    }

    record PublicVerificationView(boolean authentic) {
    }

    record RepresentationView(UUID id, UUID electronicDocumentUuid, String documentNumber, UUID issuedBy,
                              String issuedByRole, Instant issuedAt, String sha256, String keyId, String seal) {

        static RepresentationView from(GraphicRepresentation representation) {
            return new RepresentationView(representation.id(), representation.electronicDocumentUuid(),
                    representation.documentNumber(), representation.issuedBy(), representation.issuedByRole(),
                    representation.issuedAt(), representation.sha256(), representation.keyId(),
                    representation.seal());
        }
    }

    record VerificationView(RepresentationView representation, boolean documentMatches, boolean sealValid,
                            boolean authentic) {
    }
}
