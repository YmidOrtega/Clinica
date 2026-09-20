package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.AuthorizationCommands;
import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.AuthorizationResponses.AuthorizationView;
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
@RequestMapping(AuthorizationController.BASE_PATH)
@Tag(name = "Autorizaciones", description = "Autorizaciones del pagador sobre un episodio")
class AuthorizationController {

    static final String BASE_PATH = "/api/v1/admissions";

    private final AuthorizationCommands commands;

    AuthorizationController(AuthorizationCommands commands) {
        this.commands = commands;
    }

    @PostMapping("/episodes/{admissionUuid}/authorizations")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Registrar una autorización del pagador",
            description = "Sin authorizedItems la autorización cubre todo el portafolio del contrato")
    ResponseEntity<AuthorizationView> grant(@PathVariable UUID admissionUuid,
                                            @Valid @RequestBody AuthorizationRequests.Grant request) {
        Authorization granted = commands.grant(admissionUuid, request.number(), request.type(),
                request.authorizedBy(), request.copayment(), request.validFrom(), request.validTo(),
                request.authorizedItems());
        return ResponseEntity.created(URI.create(BASE_PATH + "/authorizations/" + granted.uuid()))
                .eTag(EntityTags.of(granted.version()))
                .body(AuthorizationView.from(granted));
    }

    @GetMapping("/episodes/{admissionUuid}/authorizations")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las autorizaciones de un episodio, vigentes y revocadas")
    List<AuthorizationView> ofAdmission(@PathVariable UUID admissionUuid) {
        return commands.ofAdmission(admissionUuid).stream().map(AuthorizationView::from).toList();
    }

    @PostMapping("/authorizations/{uuid}/revocation")
    @PreAuthorize(Access.ADMIT)
    @Operation(summary = "Revocar una autorización sin borrarla")
    ResponseEntity<AuthorizationView> revoke(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @Valid @RequestBody AuthorizationRequests.Revocation request) {
        Authorization revoked = commands.revoke(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return ResponseEntity.ok().eTag(EntityTags.of(revoked.version())).body(AuthorizationView.from(revoked));
    }
}
