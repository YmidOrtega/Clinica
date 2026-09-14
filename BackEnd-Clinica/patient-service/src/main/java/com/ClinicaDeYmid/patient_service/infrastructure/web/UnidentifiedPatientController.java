package com.ClinicaDeYmid.patient_service.infrastructure.web;

import com.ClinicaDeYmid.patient_service.application.UnidentifiedPatientCommands;
import com.ClinicaDeYmid.patient_service.application.UnidentifiedPatientQueries;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.infrastructure.web.PatientResponses.UnidentifiedPatientView;
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
import java.util.UUID;

@RestController
@RequestMapping(UnidentifiedPatientController.BASE_PATH)
@Tag(name = "Unidentified patients", description = "Pacientes sin identificar atendidos en urgencias")
class UnidentifiedPatientController {

    static final String BASE_PATH = "/api/v1/unidentified-patients";

    private final UnidentifiedPatientCommands commands;
    private final UnidentifiedPatientQueries queries;

    UnidentifiedPatientController(UnidentifiedPatientCommands commands, UnidentifiedPatientQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.REGISTER_UNIDENTIFIED)
    @Operation(summary = "Registrar un paciente sin identificar")
    ResponseEntity<UnidentifiedPatientView> register(@RequestBody PatientRequests.UnidentifiedRegistration request) {
        if (request.estimatedBirthYear() == null) {
            throw new PatientException.InvalidData("estimatedBirthYear", "es obligatorio");
        }
        UnidentifiedPatient patient = commands.register(request.sex(), request.estimatedBirthYear(), request.description());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + patient.uuid()))
                .eTag(EntityTags.of(patient.version()))
                .body(UnidentifiedPatientView.from(patient));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un paciente sin identificar")
    ResponseEntity<UnidentifiedPatientView> get(@PathVariable UUID uuid) {
        return respond(queries.get(uuid));
    }

    @PostMapping("/{uuid}/identification")
    @PreAuthorize(Access.IDENTIFY)
    @Operation(summary = "Identificar como un paciente registrado o registrar al paciente real",
            description = "Envía patientUuid para vincular con un paciente existente, o registration para registrarlo y vincularlo en la misma transacción")
    ResponseEntity<UnidentifiedPatientView> identify(@PathVariable UUID uuid,
                                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                     @Valid @RequestBody PatientRequests.Identification request) {
        long version = EntityTags.versionFrom(ifMatch);
        boolean existing = request.patientUuid() != null;
        if (existing == (request.registration() != null)) {
            throw new PatientException.InvalidData("identification", "debe indicar patientUuid o registration, pero no ambos");
        }
        return respond(existing
                ? commands.identifyAsExisting(uuid, version, request.patientUuid(), request.reason())
                : commands.identifyAsNew(uuid, version, request.registration().toDomain(), request.reason()));
    }

    @PostMapping("/{uuid}/identification-reversal")
    @PreAuthorize(Access.IDENTIFY)
    @Operation(summary = "Revertir una identificación equivocada")
    ResponseEntity<UnidentifiedPatientView> revert(@PathVariable UUID uuid,
                                                   @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                   @RequestBody PatientRequests.Reversal request) {
        return respond(commands.revertIdentification(uuid, EntityTags.versionFrom(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/death")
    @PreAuthorize(Access.RECORD_DEATH)
    @Operation(summary = "Registrar el fallecimiento de un paciente sin identificar")
    ResponseEntity<UnidentifiedPatientView> recordDeath(@PathVariable UUID uuid,
                                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                        @RequestBody PatientRequests.Death request) {
        return respond(commands.recordDeath(uuid, EntityTags.versionFrom(ifMatch), request.dateOfDeath()));
    }

    private static ResponseEntity<UnidentifiedPatientView> respond(UnidentifiedPatient patient) {
        return ResponseEntity.ok()
                .eTag(EntityTags.of(patient.version()))
                .body(UnidentifiedPatientView.from(patient));
    }
}
