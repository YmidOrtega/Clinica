package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.attachment.AttachmentCommands;
import com.ClinicaDeYmid.clinical_history_service.application.attachment.AttachmentQueries;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachment;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.AttachmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@PreAuthorize(Access.CLINICAL_STAFF)
@Tag(name = "Attachments", description = "Anexos PDF, JPEG o PNG de las notas, guardados cifrados en almacenamiento WORM")
class AttachmentController {

    private final AttachmentCommands commands;
    private final AttachmentQueries queries;
    private final CurrentClinician clinician;

    AttachmentController(AttachmentCommands commands, AttachmentQueries queries, CurrentClinician clinician) {
        this.commands = commands;
        this.queries = queries;
        this.clinician = clinician;
    }

    @PostMapping(path = "/drafts/{draftId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Adjuntar un archivo a uno de mis borradores",
            description = "Solo PDF, JPEG o PNG de máximo 20 MB, validados por su contenido; queda en espera hasta firmar")
    ResponseEntity<AttachmentView> attach(@PathVariable UUID draftId, @RequestPart("file") MultipartFile file) throws IOException {
        DraftAttachment attached = commands.attach(draftId, file.getOriginalFilename(), file.getBytes(), clinician.require());
        return ResponseEntity.created(URI.create(EncounterController.BASE_PATH + "/drafts/" + draftId + "/attachments/" + attached.attachment().id()))
                .body(AttachmentView.from(attached.attachment()));
    }

    @GetMapping("/drafts/{draftId}/attachments")
    @Operation(summary = "Listar los anexos de uno de mis borradores")
    List<AttachmentView> draftAttachments(@PathVariable UUID draftId) {
        return queries.ofDraft(draftId, clinician.require()).stream().map(attached -> AttachmentView.from(attached.attachment())).toList();
    }

    @DeleteMapping("/drafts/{draftId}/attachments/{attachmentId}")
    @Operation(summary = "Quitar un anexo de uno de mis borradores")
    ResponseEntity<Void> detach(@PathVariable UUID draftId, @PathVariable UUID attachmentId) {
        commands.detach(draftId, attachmentId, clinician.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notes/{noteId}/attachments/{attachmentId}")
    @Operation(summary = "Descargar un anexo de una nota firmada",
            description = "Verifica su SHA-256 contra la nota firmada y queda registrado en la auditoría de accesos")
    ResponseEntity<byte[]> download(@PathVariable UUID noteId, @PathVariable UUID attachmentId) {
        AttachmentQueries.Download download = queries.download(noteId, attachmentId, clinician.require());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().mediaType().mimeType()))
                .contentLength(download.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.attachment().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("Repr-Digest", "sha-256=:" + Base64.getEncoder().encodeToString(HexFormat.of().parseHex(download.attachment().sha256())) + ":")
                .body(download.content());
    }
}
