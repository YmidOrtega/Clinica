package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.access.CareAccessCommands;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(EncounterController.BASE_PATH)
@PreAuthorize(Access.CLINICAL_STAFF)
@Tag(name = "Care access", description = "Equipo de cuidado y acceso de emergencia a la historia clínica")
class CareAccessController {

    private final CareAccessCommands commands;
    private final CurrentClinician clinician;

    CareAccessController(CareAccessCommands commands, CurrentClinician clinician) {
        this.commands = commands;
        this.clinician = clinician;
    }

    record CareTeamMemberView(UUID encounterId, UUID clinicianUuid, String role, boolean added) {
    }

    record EmergencyAccessView(UUID id, UUID patientUuid, Instant grantedAt, Instant expiresAt) {
        static EmergencyAccessView from(EmergencyAccess access) {
            return new EmergencyAccessView(access.id(), access.patientUuid(), access.grantedAt(), access.expiresAt());
        }
    }

    @PostMapping("/encounters/{id}/care-team")
    @Operation(summary = "Agregar un profesional al equipo de cuidado de una atención abierta",
            description = "Solo lo puede hacer quien ya pertenece al equipo; da acceso a la historia del paciente")
    ResponseEntity<CareTeamMemberView> addMember(@PathVariable UUID id, @Valid @RequestBody ClinicalRequests.CareTeamMember request) {
        boolean added = commands.addCareTeamMember(id, new Clinician(request.clinicianUuid(), request.role()), clinician.require());
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new CareTeamMemberView(id, request.clinicianUuid(), request.role().name(), added));
    }

    @PostMapping("/patients/{patientUuid}/emergency-access")
    @Operation(summary = "Romper el vidrio: acceso de emergencia a la historia de un paciente",
            description = "Exige un motivo, dura 4 horas y queda registrado para auditoría")
    ResponseEntity<EmergencyAccessView> emergencyAccess(@PathVariable UUID patientUuid, @RequestBody ClinicalRequests.EmergencyAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EmergencyAccessView.from(commands.grantEmergencyAccess(patientUuid, request.reason(), clinician.requireRecentlyAuthenticated())));
    }
}
