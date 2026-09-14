package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.note.NoteCommands;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.DraftView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.NoteView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.VoidView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@PreAuthorize(Access.CLINICAL_STAFF)
@Tag(name = "Notes", description = "Borradores, firma y anulación de notas clínicas")
class NoteController {

    private final NoteCommands commands;
    private final ClinicalRecordQueries queries;
    private final CurrentClinician clinician;
    private final NoteContentReader contentReader;

    NoteController(NoteCommands commands, ClinicalRecordQueries queries, CurrentClinician clinician, NoteContentReader contentReader) {
        this.commands = commands;
        this.queries = queries;
        this.clinician = clinician;
        this.contentReader = contentReader;
    }

    @GetMapping("/drafts")
    @Operation(summary = "Listar mis borradores")
    List<DraftView> myDrafts() {
        return queries.draftsOf(clinician.require()).stream().map(DraftView::from).toList();
    }

    @GetMapping("/drafts/{id}")
    @Operation(summary = "Consultar uno de mis borradores")
    ResponseEntity<DraftView> draft(@PathVariable UUID id) {
        return draftResponse(queries.draft(id, clinician.require()));
    }

    @PutMapping("/drafts/{id}")
    @Operation(summary = "Guardar cambios en uno de mis borradores")
    ResponseEntity<DraftView> revise(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                     @Valid @RequestBody ClinicalRequests.Draft request) {
        long version = EntityTags.requiredVersion(ifMatch);
        return draftResponse(commands.reviseDraft(id, version, contentReader.read(request.content()), request.occurredAt(),
                clinician.require()));
    }

    @DeleteMapping("/drafts/{id}")
    @Operation(summary = "Descartar uno de mis borradores")
    ResponseEntity<Void> discard(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        commands.discardDraft(id, EntityTags.requiredVersion(ifMatch), clinician.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts/{id}/signature")
    @Operation(summary = "Firmar el borrador", description = "Exige una sesión emitida hace menos de 15 minutos; la nota pasa al registro inmutable, queda sellada en la cadena del paciente y el borrador desaparece")
    ResponseEntity<NoteView> sign(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        SignedNote note = commands.sign(id, EntityTags.requiredVersion(ifMatch), clinician.requireSigner());
        return ResponseEntity.created(URI.create(EncounterController.BASE_PATH + "/notes/" + note.id())).body(NoteView.from(note));
    }

    @GetMapping("/notes/{id}")
    @Operation(summary = "Consultar una nota firmada")
    NoteView note(@PathVariable UUID id) {
        return NoteView.from(queries.note(id));
    }

    @PostMapping("/notes/{id}/void")
    @Operation(summary = "Anular una nota propia", description = "La nota sigue visible marcada como anulada, con motivo, autor y fecha")
    VoidView voidNote(@PathVariable UUID id, @RequestBody ClinicalRequests.Voiding request) {
        return VoidView.from(commands.voidNote(id, request.reason(), clinician.require()));
    }

    private static ResponseEntity<DraftView> draftResponse(NoteDraft draft) {
        return ResponseEntity.ok().eTag(EntityTags.of(draft.version())).body(DraftView.from(draft));
    }
}
