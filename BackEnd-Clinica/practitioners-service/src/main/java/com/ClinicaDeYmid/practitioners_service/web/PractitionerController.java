package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerService;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerViews;
import com.ClinicaDeYmid.practitioners_service.web.PractitionerResponses.PractitionerView;
import com.ClinicaDeYmid.practitioners_service.web.PractitionerResponses.RevisionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping(PractitionerController.BASE_PATH)
@Tag(name = "Practitioners", description = "Directorio de profesionales de la salud de la clínica")
class PractitionerController {

    static final String BASE_PATH = "/api/v1/practitioners";

    private final PractitionerService practitioners;

    PractitionerController(PractitionerService practitioners) {
        this.practitioners = practitioners;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Registrar un profesional")
    ResponseEntity<PractitionerView> register(@RequestBody PractitionerRequests.NewPractitioner request) {
        PractitionerViews.PractitionerView registered = practitioners.register(request.toCommand());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + registered.uuid()))
                .eTag(EntityTags.of(registered.version()))
                .body(PractitionerView.from(registered));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un profesional")
    ResponseEntity<PractitionerView> get(@PathVariable UUID uuid) {
        return respond(practitioners.get(uuid));
    }

    @PostMapping("/search")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Buscar por documento, registro profesional, apellidos o especialidad",
            description = "El documento y el registro no viajan en la URL")
    List<PractitionerView> search(@RequestBody PractitionerRequests.Search request) {
        return practitioners.search(request.toCommand()).stream().map(PractitionerView::from).toList();
    }

    @PutMapping("/{uuid}/identity")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Corregir el documento y los nombres")
    ResponseEntity<PractitionerView> correctIdentity(@PathVariable UUID uuid,
                                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                     @RequestBody PractitionerRequests.Identity request) {
        return respond(practitioners.correctIdentity(uuid, EntityTags.requiredVersion(ifMatch), request.toCommand()));
    }

    @PutMapping("/{uuid}/registration")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Corregir el registro profesional")
    ResponseEntity<PractitionerView> correctRegistration(@PathVariable UUID uuid,
                                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                         @RequestBody PractitionerRequests.Registration request) {
        return respond(practitioners.correctRegistration(uuid, EntityTags.requiredVersion(ifMatch), request.toCommand()));
    }

    @PutMapping("/{uuid}/contact")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Actualizar el contacto")
    ResponseEntity<PractitionerView> correctContact(@PathVariable UUID uuid,
                                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                    @RequestBody PractitionerRequests.Contact request) {
        return respond(practitioners.correctContact(uuid, EntityTags.requiredVersion(ifMatch), request.toCommand()));
    }

    @PutMapping("/{uuid}/relationship")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Cambiar el tipo de vínculo con la clínica")
    ResponseEntity<PractitionerView> agreeRelationship(@PathVariable UUID uuid,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @RequestBody PractitionerRequests.Relationship request) {
        return respond(practitioners.agreeRelationship(uuid, EntityTags.requiredVersion(ifMatch), request.relationship()));
    }

    @PutMapping("/{uuid}/specialties")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Asignar las especialidades del profesional, con exactamente una principal")
    ResponseEntity<PractitionerView> assignSpecialties(@PathVariable UUID uuid,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @RequestBody PractitionerRequests.Assignments request) {
        return respond(practitioners.assignSpecialties(uuid, EntityTags.requiredVersion(ifMatch), request.toCommand()));
    }

    @PutMapping("/{uuid}/account")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Vincular la cuenta del profesional",
            description = "La cuenta debe existir en la copia de auth.users.v1; si esa copia no está al día, responde 503")
    ResponseEntity<PractitionerView> linkAccount(@PathVariable UUID uuid,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                 @RequestBody PractitionerRequests.AccountLink request) {
        return respond(practitioners.linkAccount(uuid, EntityTags.requiredVersion(ifMatch), request.userUuid()));
    }

    @DeleteMapping("/{uuid}/account")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Desvincular la cuenta del profesional")
    ResponseEntity<PractitionerView> unlinkAccount(@PathVariable UUID uuid,
                                                   @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(practitioners.unlinkAccount(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/suspension")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Suspender a un profesional sin sacarlo del directorio")
    ResponseEntity<PractitionerView> suspend(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @RequestBody PractitionerRequests.StatusChange request) {
        return respond(practitioners.suspend(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Retirar a un profesional; nunca se borra")
    ResponseEntity<PractitionerView> retire(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @RequestBody PractitionerRequests.StatusChange request) {
        return respond(practitioners.retire(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reinstatement")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Devolver al directorio a un profesional suspendido o retirado")
    ResponseEntity<PractitionerView> reinstate(@PathVariable UUID uuid,
                                               @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(practitioners.reinstate(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @GetMapping("/{uuid}/history")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Historial de cambios del profesional")
    List<RevisionView> history(@PathVariable UUID uuid) {
        return practitioners.history(uuid).stream().map(RevisionView::from).toList();
    }

    private ResponseEntity<PractitionerView> respond(PractitionerViews.PractitionerView practitioner) {
        return ResponseEntity.ok().eTag(EntityTags.of(practitioner.version())).body(PractitionerView.from(practitioner));
    }
}
