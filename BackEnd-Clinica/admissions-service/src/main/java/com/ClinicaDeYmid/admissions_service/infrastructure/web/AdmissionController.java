package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.AdmissionCommands;
import com.ClinicaDeYmid.admissions_service.application.AdmissionQueries;
import com.ClinicaDeYmid.admissions_service.application.patient.UnidentifiedAdmissionRequest;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Companion;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AdmissionResponses.AdmissionView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AdmissionController.BASE_PATH)
@Tag(name = "Admisiones", description = "Ingreso del paciente, cambio de fase y cierre del episodio")
class AdmissionController {

    static final String BASE_PATH = "/api/v1/admissions/episodes";

    private final AdmissionCommands commands;
    private final AdmissionQueries queries;

    AdmissionController(AdmissionCommands commands, AdmissionQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Admitir a un paciente conocido")
    ResponseEntity<AdmissionView> register(@Valid @RequestBody AdmissionRequests.Registration request) {
        Admission admission = commands.register(request.patientUuid(), request.configurationServiceUuid(),
                request.cause(), request.careTypeUuid(), companionOf(request.companion()));
        return created(admission);
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

    @GetMapping("/by-number/{number}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un episodio por su número")
    ResponseEntity<AdmissionView> byNumber(@PathVariable String number) {
        return tagged(queries.byNumber(number));
    }

    @GetMapping("/patients/{patientUuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los episodios de un paciente")
    List<AdmissionView> ofPatient(@PathVariable UUID patientUuid) {
        return queries.ofPatient(patientUuid).stream().map(AdmissionView::from).toList();
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
                request.configurationServiceUuid(), request.reason()));
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
    @Operation(summary = "Egresar el episodio")
    ResponseEntity<AdmissionView> discharge(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(commands.discharge(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/cancellation")
    @PreAuthorize(Access.CANCEL)
    @Operation(summary = "Anular un episodio con un motivo")
    ResponseEntity<AdmissionView> cancel(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @Valid @RequestBody AdmissionRequests.Reason request) {
        return tagged(commands.cancel(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
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
