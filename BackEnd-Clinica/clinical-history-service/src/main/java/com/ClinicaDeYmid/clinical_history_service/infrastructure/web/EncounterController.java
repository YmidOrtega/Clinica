package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.encounter.EncounterCommands;
import com.ClinicaDeYmid.clinical_history_service.application.note.NoteCommands;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.DraftView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.EncounterRecordView;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.web.ClinicalResponses.EncounterView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@PreAuthorize(Access.CLINICAL_STAFF)
@Tag(name = "Encounters", description = "Atenciones clínicas y su registro")
class EncounterController {

    static final String BASE_PATH = "/api/v1/clinical";

    private final EncounterCommands encounters;
    private final NoteCommands notes;
    private final ClinicalRecordQueries queries;
    private final CurrentClinician clinician;
    private final NoteContentReader contentReader;

    EncounterController(EncounterCommands encounters, NoteCommands notes, ClinicalRecordQueries queries, CurrentClinician clinician,
                        NoteContentReader contentReader) {
        this.encounters = encounters;
        this.notes = notes;
        this.queries = queries;
        this.clinician = clinician;
        this.contentReader = contentReader;
    }

    @PostMapping("/encounters")
    @Operation(summary = "Abrir una atención clínica para un paciente")
    ResponseEntity<EncounterView> open(@Valid @RequestBody ClinicalRequests.OpenEncounter request) {
        Encounter encounter = encounters.open(request.patientUuid(), request.type(), request.admissionId(), clinician.require());
        return ResponseEntity.created(URI.create(BASE_PATH + "/encounters/" + encounter.id())).body(EncounterView.from(encounter));
    }

    @GetMapping("/encounters/{id}")
    @Operation(summary = "Consultar una atención con el índice de sus notas firmadas")
    EncounterRecordView get(@PathVariable UUID id) {
        return EncounterRecordView.from(queries.encounter(id));
    }

    @PostMapping("/encounters/{id}/closure")
    @Operation(summary = "Cerrar la atención", description = "Urgencias y hospitalización exigen epicrisis firmada; las demás, al menos una nota firmada")
    EncounterView close(@PathVariable UUID id) {
        return EncounterView.from(encounters.close(id, clinician.require()));
    }

    @GetMapping("/patients/{patientUuid}/encounters")
    @Operation(summary = "Listar las atenciones de un paciente, incluidas las registradas mientras estuvo sin identificar")
    List<EncounterView> ofPatient(@PathVariable UUID patientUuid,
                                  @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
                                  @RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser al menos 1") @Max(value = 100, message = "no puede superar 100") int size) {
        return queries.encountersOf(patientUuid, page, size).stream().map(EncounterView::from).toList();
    }

    @PostMapping("/encounters/{id}/drafts")
    @Operation(summary = "Iniciar el borrador de una nota clínica")
    ResponseEntity<DraftView> startDraft(@PathVariable UUID id, @Valid @RequestBody ClinicalRequests.Draft request) {
        NoteDraft draft = notes.startDraft(id, contentReader.read(request.content()), request.occurredAt(), clinician.require());
        return ResponseEntity.created(URI.create(BASE_PATH + "/drafts/" + draft.id()))
                .eTag(EntityTags.of(draft.version()))
                .body(DraftView.from(draft));
    }
}
