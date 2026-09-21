package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.AdmissionCommands;
import com.ClinicaDeYmid.admissions_service.application.AdmissionQueries;
import com.ClinicaDeYmid.admissions_service.application.BedAssignments;
import com.ClinicaDeYmid.admissions_service.application.DischargeOrder;
import com.ClinicaDeYmid.admissions_service.application.patient.UnidentifiedAdmissionRequest;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Companion;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AdmissionResponses.AdmissionSummaryView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AdmissionResponses.AdmissionView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(AdmissionController.BASE_PATH)
@Tag(name = "Admisiones", description = "Ingreso del paciente, cambio de fase y cierre del episodio")
class AdmissionController {

    static final String BASE_PATH = "/api/v1/admissions/episodes";

    private final AdmissionCommands commands;
    private final AdmissionQueries queries;
    private final BedAssignments bedAssignments;

    AdmissionController(AdmissionCommands commands, AdmissionQueries queries, BedAssignments bedAssignments) {
        this.commands = commands;
        this.queries = queries;
        this.bedAssignments = bedAssignments;
    }

    @PostMapping
    @PreAuthorize(Access.ADMIT_OR_OVERRIDE)
    @Operation(summary = "Admitir a un paciente conocido",
            description = "Sin cobertura se bloquea en hospitalización y ambulatorio; en urgencias nunca. "
                    + "overrideCoverage exige el permiso admissions:override-coverage")
    ResponseEntity<AdmissionView> register(@Valid @RequestBody AdmissionRequests.Registration request) {
        Admission admission = commands.register(request.patientUuid(), request.configurationServiceUuid(),
                request.cause(), request.careTypeUuid(), companionOf(request.companion()),
                request.overrideCoverage());
        return created(admission);
    }

    @PostMapping("/search")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Buscar episodios por documento del paciente, número, estado, tipo, servicio o fechas",
            description = "Usa POST para que el documento del paciente no quede en URLs ni en logs; el tipo y "
                    + "el servicio configurado buscan en cualquiera de las fases del episodio")
    PagedModel<AdmissionSummaryView> search(@Valid @RequestBody AdmissionRequests.Search request,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return paged(queries.search(request.toCriteria(), documentOf(request.document()), PageRequest.of(page, size)));
    }

    @GetMapping("/pending-coverage")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los episodios admitidos sin cobertura resuelta",
            description = "Alimenta la revisión administrativa y la facturación posterior")
    PagedModel<AdmissionSummaryView> pendingCoverage(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return paged(queries.withPendingCoverage(PageRequest.of(page, size)));
    }

    @GetMapping("/pending-death-notice")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los fallecimientos que no se pudieron informar al directorio de pacientes")
    PagedModel<AdmissionSummaryView> pendingDeathNotice(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return paged(queries.withPendingDeathNotice(PageRequest.of(page, size)));
    }

    @PostMapping("/unidentified")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Admitir a un paciente sin identificar",
            description = "Registra primero el NN en patient-service; si no responde, no se crea la admisión")
    ResponseEntity<AdmissionView> registerUnidentified(
            @Valid @RequestBody AdmissionRequests.UnidentifiedRegistration request) {
        Admission admission = commands.admitUnidentified(
                new UnidentifiedAdmissionRequest(request.sex(), request.estimatedBirthYear(), request.description()),
                request.configurationServiceUuid(), request.cause(), request.careTypeUuid(),
                companionOf(request.companion()));
        return created(admission);
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un episodio")
    ResponseEntity<AdmissionView> get(@PathVariable UUID uuid) {
        return tagged(queries.get(uuid));
    }

    @PostMapping("/{uuid}/activation")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Activar el episodio cuando empieza la atención")
    ResponseEntity<AdmissionView> activate(@PathVariable UUID uuid,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(commands.activate(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/phase")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Pasar el episodio a otro servicio configurado",
            description = "Cierra la fase vigente y abre otra sin cambiar el número del episodio")
    ResponseEntity<AdmissionView> changePhase(@PathVariable UUID uuid,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody AdmissionRequests.PhaseChange request) {
        return tagged(commands.moveTo(uuid, EntityTags.requiredVersion(ifMatch),
                request.configurationServiceUuid(), request.reason(), request.bedUuid()));
    }

    @PostMapping("/{uuid}/bed")
    @PreAuthorize(Access.MOVE_BED)
    @Operation(summary = "Asignar o trasladar la cama del episodio",
            description = "Libera la cama anterior y toma la nueva en una sola transacción")
    ResponseEntity<AdmissionView> assignBed(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @Valid @RequestBody AdmissionRequests.BedAssignment request) {
        return tagged(bedAssignments.assign(uuid, EntityTags.requiredVersion(ifMatch), request.bedUuid()));
    }

    @PostMapping("/{uuid}/bed-release")
    @PreAuthorize(Access.MOVE_BED)
    @Operation(summary = "Liberar la cama del episodio sin egresarlo")
    ResponseEntity<AdmissionView> releaseBed(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(bedAssignments.release(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/attending-practitioner")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Asignar el profesional responsable del episodio",
            description = "Copia su nombre y registro profesional para que el episodio los conserve")
    ResponseEntity<AdmissionView> attendedBy(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @Valid @RequestBody AdmissionRequests.AttendingPractitioner request) {
        return tagged(commands.attendedBy(uuid, EntityTags.requiredVersion(ifMatch), request.practitionerUuid()));
    }

    @PostMapping("/{uuid}/companion")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Registrar o cambiar el acompañante")
    ResponseEntity<AdmissionView> companion(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @Valid @RequestBody AdmissionRequests.CompanionPayload request) {
        return tagged(commands.accompaniedBy(uuid, EntityTags.requiredVersion(ifMatch), companionOf(request)));
    }

    @PostMapping("/{uuid}/discharge")
    @PreAuthorize(Access.DISCHARGE)
    @Operation(summary = "Egresar el episodio diciendo cómo termina",
            description = "Alta médica, alta voluntaria firmada, remisión a otra institución, fuga o "
                    + "fallecimiento; cualquiera de los cinco libera la cama y congela el episodio")
    ResponseEntity<AdmissionView> discharge(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @Valid @RequestBody AdmissionRequests.DischargePayload request) {
        return tagged(commands.discharge(uuid, EntityTags.requiredVersion(ifMatch), orderOf(request)));
    }

    @PostMapping("/{uuid}/death-notice")
    @PreAuthorize(Access.DISCHARGE)
    @Operation(summary = "Reintentar el aviso de fallecimiento al directorio de pacientes",
            description = "Para los episodios que aparecen en pending-death-notice porque patient-service "
                    + "no respondió o rechazó el aviso")
    ResponseEntity<AdmissionView> reportDeath(@PathVariable UUID uuid) {
        return tagged(commands.reportDeath(uuid));
    }

    @PostMapping("/{uuid}/cancellation")
    @PreAuthorize(Access.CANCEL)
    @Operation(summary = "Anular un episodio con un motivo")
    ResponseEntity<AdmissionView> cancel(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @Valid @RequestBody AdmissionRequests.Reason request) {
        return tagged(commands.cancel(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    private static DischargeOrder orderOf(AdmissionRequests.DischargePayload payload) {
        return new DischargeOrder(payload.type(), payload.notes(), payload.signedBy(), payload.signatureDocument(),
                payload.repsCode(), payload.facility(), payload.reason(), payload.noticedAt(), payload.occurredAt(),
                payload.certificateNumber());
    }

    private static PatientReference.Document documentOf(AdmissionRequests.Document document) {
        return document == null ? null : new PatientReference.Document(document.type(), document.number());
    }

    private static PagedModel<AdmissionSummaryView> paged(Page<Admission> page) {
        return new PagedModel<>(page.map(AdmissionSummaryView::from));
    }

    private static Companion companionOf(AdmissionRequests.CompanionPayload payload) {
        return payload == null ? null
                : Companion.of(payload.fullName(), payload.phoneNumber(), payload.relationship());
    }

    private ResponseEntity<AdmissionView> created(Admission admission) {
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + admission.uuid()))
                .eTag(EntityTags.of(admission.version()))
                .body(AdmissionView.from(admission));
    }

    private ResponseEntity<AdmissionView> tagged(Admission admission) {
        return ResponseEntity.ok().eTag(EntityTags.of(admission.version())).body(AdmissionView.from(admission));
    }
}
