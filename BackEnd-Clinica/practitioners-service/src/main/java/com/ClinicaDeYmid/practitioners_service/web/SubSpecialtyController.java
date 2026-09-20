package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueService;
import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews;
import com.ClinicaDeYmid.practitioners_service.web.CatalogueResponses.SubSpecialtyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(SubSpecialtyController.BASE_PATH)
@Tag(name = "Sub-specialties", description = "Subespecialidades, que siempre cuelgan de una especialidad")
class SubSpecialtyController {

    static final String BASE_PATH = "/api/v1/sub-specialties";

    private final CatalogueService catalogue;

    SubSpecialtyController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Corregir el nombre de una subespecialidad")
    ResponseEntity<SubSpecialtyView> rename(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @RequestBody CatalogueRequests.Rename request) {
        return respond(catalogue.renameSubSpecialty(uuid, EntityTags.requiredVersion(ifMatch), request.name()));
    }

    @PostMapping("/{uuid}/deactivation")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Desactivar una subespecialidad")
    ResponseEntity<SubSpecialtyView> deactivate(@PathVariable UUID uuid,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @RequestBody CatalogueRequests.StatusChange request) {
        return respond(catalogue.deactivateSubSpecialty(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    @PreAuthorize(Access.MANAGE)
    @Operation(summary = "Volver a activar una subespecialidad, si su especialidad sigue activa")
    ResponseEntity<SubSpecialtyView> reactivate(@PathVariable UUID uuid,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(catalogue.reactivateSubSpecialty(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    private ResponseEntity<SubSpecialtyView> respond(CatalogueViews.SubSpecialtyView subSpecialty) {
        return ResponseEntity.ok().eTag(EntityTags.of(subSpecialty.version())).body(SubSpecialtyView.from(subSpecialty));
    }
}
