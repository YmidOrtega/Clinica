package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.copy.RecordCopyService;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopy;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.EcdsaClinicalSignature;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
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
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@PreAuthorize(Access.RECORDS_OFFICE)
@Tag(name = "Record copies", description = "Copias certificables de la historia clínica para el paciente")
class RecordCopyController {

    private final RecordCopyService copies;
    private final EcdsaClinicalSignature signature;
    private final CurrentUser currentUser;

    RecordCopyController(RecordCopyService copies, EcdsaClinicalSignature signature, CurrentUser currentUser) {
        this.copies = copies;
        this.signature = signature;
        this.currentUser = currentUser;
    }

    record CopyRequest(String reason, Instant from, Instant to) {
    }

    record CopyView(UUID id, UUID patientUuid, UUID requestedBy, String requestedRole, String reason, Instant periodFrom, Instant periodTo,
                    int entries, boolean chainVerified, String documentSha256, String sealAlgorithm, String keyId, String seal, Instant generatedAt) {
        static CopyView from(RecordCopy copy, String algorithm) {
            return new CopyView(copy.id(), copy.patientUuid(), copy.requestedBy(), copy.requestedRole(), copy.reason(), copy.periodFrom(),
                    copy.periodTo(), copy.entries(), copy.chainVerified(), copy.documentSha256(), algorithm, copy.keyId(), copy.seal(),
                    copy.generatedAt());
        }
    }

    record VerificationView(UUID copyId, boolean authentic, boolean documentMatches, boolean sealValid, CopyView copy) {
    }

    @PostMapping(path = "/patients/{patientUuid}/record-copies", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generar la copia en PDF de la historia clínica para entregar al paciente",
            description = "Exige motivo; incluye notas restringidas, anulaciones, sellos y una página de verificación; queda auditada")
    ResponseEntity<byte[]> generate(@PathVariable UUID patientUuid, @RequestBody CopyRequest request) {
        AuthenticatedUser user = currentUser.get().orElseThrow();
        RecordCopyService.Generated generated = copies.generate(patientUuid, request.reason(), request.from(), request.to(),
                new RecordCopyService.Requester(UUID.fromString(user.uuid()), user.role()), signature.algorithm(), signature.activeKeyId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("historia-clinica-" + generated.copy().id() + ".pdf").build().toString())
                .header("X-Record-Copy-Id", generated.copy().id().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(generated.document());
    }

    @GetMapping("/record-copies/{copyId}")
    @Operation(summary = "Consultar el registro y el sello de una copia emitida")
    CopyView copy(@PathVariable UUID copyId) {
        return CopyView.from(copies.find(copyId), signature.algorithm());
    }

    @PostMapping(path = "/record-copies/{copyId}/verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Verificar que un PDF es exactamente la copia emitida y que su sello institucional es válido")
    VerificationView verify(@PathVariable UUID copyId, @RequestPart("file") MultipartFile file) throws IOException {
        RecordCopyService.Verification verification = copies.verify(copyId, file.getBytes());
        return new VerificationView(copyId, verification.documentMatches() && verification.sealValid(), verification.documentMatches(),
                verification.sealValid(), CopyView.from(verification.copy(), signature.algorithm()));
    }
}
