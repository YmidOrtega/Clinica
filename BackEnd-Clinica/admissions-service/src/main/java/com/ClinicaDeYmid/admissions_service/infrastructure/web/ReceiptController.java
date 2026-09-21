package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.receipt.ReceiptService;
import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipt;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ReceiptController.BASE_PATH)
@Tag(name = "Comprobantes", description = "Comprobante sellado del episodio y su verificación")
class ReceiptController {

    static final String BASE_PATH = "/api/v1/admissions";

    private final ReceiptService receipts;
    private final DocumentSealer sealer;
    private final CurrentUser currentUser = new CurrentUser();

    ReceiptController(ReceiptService receipts, DocumentSealer sealer) {
        this.receipts = receipts;
        this.sealer = sealer;
    }

    record ReceiptView(UUID id, UUID admissionUuid, String admissionNumber, UUID issuedBy, String issuedByRole,
                       Instant issuedAt, String documentSha256, String keyId, String seal) {

        static ReceiptView from(EpisodeReceipt receipt) {
            return new ReceiptView(receipt.id(), receipt.admissionUuid(), receipt.admissionNumber(),
                    receipt.issuedBy(), receipt.issuedByRole(), receipt.issuedAt(), receipt.sha256(),
                    receipt.keyId(), receipt.seal());
        }
    }

    record VerificationView(ReceiptView receipt, boolean documentMatches, boolean sealValid, boolean authentic) {
    }

    record PublicCheck(@NotBlank String number,
                       @Pattern(regexp = "^[0-9a-f]{64}$", message = "debe ser una huella SHA-256 en hexadecimal")
                       String sha256) {
    }

    record PublicVerificationView(boolean authentic) {
    }

    @PostMapping(path = "/episodes/{uuid}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Emitir el comprobante del episodio en PDF",
            description = "Registra su huella SHA-256 y el sello institucional; el documento no se guarda")
    ResponseEntity<byte[]> issue(@PathVariable UUID uuid) {
        AuthenticatedUser user = currentUser.get().orElseThrow();
        ReceiptService.Issued issued = receipts.issue(uuid,
                new ReceiptService.Issuer(user.uuid(), user.role(), user.name()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + issued.receipt().admissionNumber() + ".pdf\"")
                .header("X-Receipt-Id", issued.receipt().id().toString())
                .header("X-Document-Sha256", issued.receipt().sha256())
                .body(issued.document());
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar el registro y el sello de un comprobante emitido")
    ReceiptView get(@PathVariable UUID id) {
        return ReceiptView.from(receipts.find(id));
    }

    @PostMapping(path = "/receipts/{id}/verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Verificar que un PDF es exactamente el comprobante emitido y que su sello es válido")
    VerificationView verify(@PathVariable UUID id, @RequestPart("document") MultipartFile document) {
        ReceiptService.Verified verified = receipts.verify(id, bytesOf(document));
        return new VerificationView(ReceiptView.from(verified.receipt()), verified.verification().documentMatches(),
                verified.verification().sealValid(), verified.verification().authentic());
    }

    @PostMapping("/receipts/verification")
    @Operation(summary = "Comprobar públicamente si una huella corresponde a un comprobante auténtico",
            description = "No exige credenciales y no revela ningún dato del episodio ni del paciente")
    PublicVerificationView verifyPublicly(@Valid @RequestBody PublicCheck check) {
        return new PublicVerificationView(receipts.authentic(check.number(), check.sha256()));
    }

    @GetMapping("/seal-keys")
    @Operation(summary = "Publicar las claves públicas con las que se sellan los comprobantes")
    Map<String, String> sealKeys() {
        return sealer.publicKeysPem();
    }

    private static byte[] bytesOf(MultipartFile document) {
        try {
            return document.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
