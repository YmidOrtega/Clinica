package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueService;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews;
import com.ClinicaDeYmid.practitioners_service.web.CatalogueResponses.ImportView;
import com.ClinicaDeYmid.practitioners_service.web.CatalogueResponses.SpecialtyView;
import com.ClinicaDeYmid.practitioners_service.web.CatalogueResponses.SubSpecialtyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping(SpecialtyController.BASE_PATH)
@Tag(name = "Specialties", description = "Catálogo de especialidades y subespecialidades de la clínica")
class SpecialtyController {

    static final String BASE_PATH = "/api/v1/specialties";

    private final CatalogueService catalogue;

    SpecialtyController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Registrar una especialidad")
    ResponseEntity<SpecialtyView> register(@RequestBody CatalogueRequests.Specialty request) {
        CatalogueViews.SpecialtyView registered = catalogue.register(request.toCommand());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + registered.uuid()))
                .eTag(EntityTags.of(registered.version()))
                .body(SpecialtyView.from(registered));
    }

    @PostMapping("/imports")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Cargar el catálogo completo",
            description = "Idempotente por código: crea las que faltan, corrige los nombres que cambiaron y deja intactas las iguales")
    ImportView importCatalogue(@Valid @RequestBody CatalogueRequests.Import request) {
        return ImportView.from(catalogue.importCatalogue(request.toCommands()));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una especialidad con sus subespecialidades")
    ResponseEntity<SpecialtyView> get(@PathVariable UUID uuid) {
        return respond(catalogue.get(uuid));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar el catálogo, filtrando por estado o por prefijo del nombre")
    List<SpecialtyView> search(@RequestParam(required = false) CatalogueCommands.StatusFilter status,
                               @RequestParam(required = false) String name) {
        return catalogue.search(status, name).stream()
                .map(SpecialtyView::from)
                .toList();
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Corregir el nombre de una especialidad")
    ResponseEntity<SpecialtyView> rename(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @RequestBody CatalogueRequests.Rename request) {
        return respond(catalogue.rename(uuid, EntityTags.requiredVersion(ifMatch), request.name()));
    }

    @PostMapping("/{uuid}/deactivation")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Desactivar una especialidad y, con ella, sus subespecialidades activas")
    ResponseEntity<SpecialtyView> deactivate(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @RequestBody CatalogueRequests.StatusChange request) {
        return respond(catalogue.deactivate(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Volver a activar una especialidad")
    ResponseEntity<SpecialtyView> reactivate(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(catalogue.reactivate(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/sub-specialties")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Agregar una subespecialidad a la especialidad")
    ResponseEntity<SubSpecialtyView> addSubSpecialty(@PathVariable UUID uuid,
                                                     @RequestBody CatalogueRequests.SubSpecialty request) {
        CatalogueViews.SubSpecialtyView added = catalogue.addSubSpecialty(uuid, request.toCommand());
        return ResponseEntity.created(URI.create(SubSpecialtyController.BASE_PATH + "/" + added.uuid()))
                .eTag(EntityTags.of(added.version()))
                .body(SubSpecialtyView.from(added));
    }

    private ResponseEntity<SpecialtyView> respond(CatalogueViews.SpecialtyView specialty) {
        return ResponseEntity.ok().eTag(EntityTags.of(specialty.version())).body(SpecialtyView.from(specialty));
    }
}
