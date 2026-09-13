package com.ClinicaDeYmid.patient_service.infrastructure.web;

import com.ClinicaDeYmid.patient_service.application.PatientCommands;
import com.ClinicaDeYmid.patient_service.application.PatientQueries;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.HealthProviderView;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.PatientDetailsView;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.PatientSummaryView;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.PatientView;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.RevisionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(PatientController.BASE_PATH)
@Tag(name = "Patients", description = "Registro administrativo de pacientes")
class PatientController {

    static final String BASE_PATH = "/api/v1/patients";

    private final PatientCommands commands;
    private final PatientQueries queries;

    PatientController(PatientCommands commands, PatientQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Registrar un paciente")
    ResponseEntity<PatientView> register(@Valid @RequestBody PatientRequests.Register request) {
        Patient patient = commands.register(request.toDomain());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + patient.uuid()))
                .eTag(EntityTags.of(patient.version()))
                .body(PatientView.from(patient));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un paciente con el estado de su aseguradora")
    ResponseEntity<PatientDetailsView> get(@PathVariable UUID uuid) {
        PatientQueries.PatientDetails details = queries.get(uuid);
        return ResponseEntity.ok()
                .eTag(EntityTags.of(details.patient().version()))
                .body(new PatientDetailsView(PatientView.from(details.patient()), HealthProviderView.from(details.healthProvider())));
    }

    @PostMapping("/search")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Buscar pacientes por documento exacto o por prefijo de apellidos y nombres",
            description = "Usa POST para que el documento y el nombre no queden en URLs ni en logs de acceso")
    PagedModel<PatientSummaryView> search(@Valid @RequestBody PatientRequests.Search request,
                                          @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
                                          @RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser al menos 1") @Max(value = 50, message = "no puede superar 50") int size) {
        boolean byDocument = request.document() != null;
        boolean byName = request.name() != null;
        if (byDocument == byName) {
            throw new PatientException.InvalidData("search", "debe indicar document o name, pero no ambos");
        }
        if (byDocument) {
            List<PatientSummaryView> matches = queries.findByDocument(request.document().toDomain())
                    .map(PatientSummaryView::from)
                    .stream()
                    .toList();
            return new PagedModel<>(new PageImpl<>(matches, PageRequest.of(0, size), matches.size()));
        }
        return new PagedModel<>(queries.searchByName(request.name().lastNames(), request.name().firstNames(), PageRequest.of(page, size))
                .map(PatientSummaryView::from));
    }

    @PutMapping("/{uuid}/document")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Cambiar el documento de identidad")
    ResponseEntity<PatientView> changeDocument(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                               @RequestBody PatientRequests.Document request) {
        return respond(commands.changeDocument(uuid, EntityTags.versionFrom(ifMatch), request.toDomain()));
    }

    @PutMapping("/{uuid}/demographics")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Corregir nombre, fecha de nacimiento, sexo, país de origen o discapacidad")
    ResponseEntity<PatientView> correctDemographics(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                    @RequestBody PatientRequests.DemographicData request) {
        return respond(commands.correctDemographics(uuid, EntityTags.versionFrom(ifMatch), request.toDomain()));
    }

    @PutMapping("/{uuid}/contact")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Actualizar contacto y contacto de emergencia")
    ResponseEntity<PatientView> updateContact(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody PatientRequests.ContactUpdate request) {
        return respond(commands.updateContact(uuid, EntityTags.versionFrom(ifMatch), request.contact().toDomain(),
                PatientRequests.Emergency.toDomain(request.emergencyContact())));
    }

    @PutMapping("/{uuid}/affiliation")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Actualizar la afiliación en salud")
    ResponseEntity<PatientView> updateAffiliation(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                  @RequestBody PatientRequests.AffiliationData request) {
        return respond(commands.updateAffiliation(uuid, EntityTags.versionFrom(ifMatch), request.toDomain()));
    }

    @PutMapping("/{uuid}/residence")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Actualizar la residencia")
    ResponseEntity<PatientView> updateResidence(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @RequestBody PatientRequests.ResidenceData request) {
        return respond(commands.updateResidence(uuid, EntityTags.versionFrom(ifMatch), request.toDomain()));
    }

    @PostMapping("/{uuid}/deactivation")
    @PreAuthorize(Access.CHANGE_STATUS)
    @Operation(summary = "Desactivar un paciente")
    ResponseEntity<PatientView> deactivate(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                           @RequestBody PatientRequests.Deactivation request) {
        return respond(commands.deactivate(uuid, EntityTags.versionFrom(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    @PreAuthorize(Access.CHANGE_STATUS)
    @Operation(summary = "Reactivar un paciente")
    ResponseEntity<PatientView> reactivate(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(commands.reactivate(uuid, EntityTags.versionFrom(ifMatch)));
    }

    @PostMapping("/{uuid}/death")
    @PreAuthorize(Access.RECORD_DEATH)
    @Operation(summary = "Registrar el fallecimiento")
    ResponseEntity<PatientView> recordDeath(@PathVariable UUID uuid, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @RequestBody PatientRequests.Death request) {
        return respond(commands.recordDeath(uuid, EntityTags.versionFrom(ifMatch), request.dateOfDeath()));
    }

    @GetMapping("/{uuid}/history")
    @PreAuthorize(Access.AUDIT)
    @Operation(summary = "Consultar el historial de cambios del paciente")
    List<RevisionView> history(@PathVariable UUID uuid) {
        return queries.history(uuid).stream().map(RevisionView::from).toList();
    }

    private static ResponseEntity<PatientView> respond(Patient patient) {
        return ResponseEntity.ok()
                .eTag(EntityTags.of(patient.version()))
                .body(PatientView.from(patient));
    }
}
