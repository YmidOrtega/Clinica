package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.NumberingCommands;
import com.ClinicaDeYmid.billing_service.application.NumberingQueries;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.infrastructure.web.NumberingResponses.ResolutionView;
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
@RequestMapping(NumberingController.BASE_PATH)
@Tag(name = "Numeración", description = "Resoluciones de numeración de facturas autorizadas por la DIAN")
class NumberingController {

    static final String BASE_PATH = "/api/v1/billing/numbering-resolutions";

    private final NumberingCommands commands;
    private final NumberingQueries queries;

    NumberingController(NumberingCommands commands, NumberingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Registrar una resolución de numeración",
            description = "Queda pendiente hasta que se active; el rango no puede cruzarse con otro del mismo prefijo")
    ResponseEntity<ResolutionView> register(@Valid @RequestBody NumberingRequests.Registration request) {
        NumberingResolution resolution = commands.register(request.toDomain());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + resolution.uuid()))
                .eTag(EntityTags.of(resolution.version()))
                .body(ResolutionView.from(queries.resolution(resolution.uuid())));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las resoluciones con su consumo y sus alertas")
    List<ResolutionView> resolutions() {
        return queries.resolutions().stream().map(ResolutionView::from).toList();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una resolución")
    ResponseEntity<ResolutionView> resolution(@PathVariable UUID uuid) {
        return tagged(uuid);
    }

    @PostMapping("/{uuid}/activation")
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Activar una resolución para numerar las facturas",
            description = "Solo puede haber una activa; la que estaba activa queda retirada")
    ResponseEntity<ResolutionView> activate(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        commands.activate(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(uuid);
    }

    @PostMapping("/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Retirar una resolución con un motivo")
    ResponseEntity<ResolutionView> retire(@PathVariable UUID uuid,
                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                          @Valid @RequestBody NumberingRequests.Reason request) {
        commands.retire(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(uuid);
    }

    private ResponseEntity<ResolutionView> tagged(UUID uuid) {
        NumberingQueries.ResolutionState state = queries.resolution(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(state.resolution().version())).body(ResolutionView.from(state));
    }
}
