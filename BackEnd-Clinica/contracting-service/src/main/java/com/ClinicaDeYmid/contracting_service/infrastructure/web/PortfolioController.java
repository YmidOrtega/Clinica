package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.application.PortfolioCommands;
import com.ClinicaDeYmid.contracting_service.application.PortfolioQueries;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PortfolioResponses.ImportView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PortfolioResponses.ItemView;
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
@RequestMapping(PortfolioController.BASE_PATH)
@Tag(name = "Portfolio", description = "Portafolio de servicios de la clínica, con su código CUPS y su código propio")
class PortfolioController {

    static final String BASE_PATH = "/api/v1/portfolio-items";

    private final PortfolioCommands commands;
    private final PortfolioQueries queries;

    PortfolioController(PortfolioCommands commands, PortfolioQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_PORTFOLIO)
    @Operation(summary = "Agregar un servicio al portafolio")
    ResponseEntity<ItemView> offer(@RequestBody PortfolioRequests.Item request) {
        PortfolioItem item = commands.offer(request.code(), request.name(), request.category());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + item.uuid()))
                .eTag(EntityTags.of(item.version()))
                .body(ItemView.from(item));
    }

    @PostMapping("/imports")
    @PreAuthorize(Access.MANAGE_PORTFOLIO)
    @Operation(summary = "Cargar el portafolio completo",
            description = "Idempotente por código de la clínica: crea los que faltan, corrige los que cambiaron y deja intactos los iguales")
    ImportView importAll(@Valid @RequestBody PortfolioRequests.Import request) {
        return ImportView.from(commands.importAll(request.toEntries()));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un servicio del portafolio")
    ResponseEntity<ItemView> get(@PathVariable UUID uuid) {
        return respond(queries.get(uuid));
    }

    @PostMapping("/search")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Buscar por código de la clínica, código CUPS o prefijo del nombre")
    PagedModel<ItemView> search(@RequestBody PortfolioRequests.Search request,
                                @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
                                @RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser al menos 1")
                                @Max(value = 50, message = "no puede superar 50") int size) {
        long criteria = List.of(request.clinicCode() != null, request.cupsCode() != null, request.name() != null).stream()
                .filter(Boolean::booleanValue)
                .count();
        if (criteria != 1) {
            throw new ContractingException.InvalidData("search", "debe indicar exactamente uno de clinicCode, cupsCode o name");
        }
        if (request.clinicCode() != null) {
            return page(queries.findByClinicCode(request.clinicCode().toUpperCase()), size);
        }
        if (request.cupsCode() != null) {
            return page(queries.findByCupsCode(request.cupsCode()), size);
        }
        PortfolioQueries.Page found = queries.searchByName(request.name(), page, size);
        return new PagedModel<>(new PageImpl<>(found.matches().stream().map(ItemView::from).toList(),
                PageRequest.of(page, size), found.total()));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(Access.MANAGE_PORTFOLIO)
    @Operation(summary = "Corregir el código, el nombre o la categoría del servicio")
    ResponseEntity<ItemView> describe(@PathVariable UUID uuid,
                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                      @RequestBody PortfolioRequests.Item request) {
        return respond(commands.describe(uuid, EntityTags.requiredVersion(ifMatch), request.code(), request.name(), request.category()));
    }

    @PostMapping("/{uuid}/deactivation")
    @PreAuthorize(Access.MANAGE_PORTFOLIO)
    @Operation(summary = "Sacar un servicio del portafolio sin borrarlo")
    ResponseEntity<ItemView> stopOffering(@PathVariable UUID uuid,
                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                          @RequestBody PortfolioRequests.StatusChange request) {
        return respond(commands.stopOffering(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    @PreAuthorize(Access.MANAGE_PORTFOLIO)
    @Operation(summary = "Volver a ofrecer un servicio retirado")
    ResponseEntity<ItemView> offerAgain(@PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(commands.offerAgain(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    private PagedModel<ItemView> page(List<PortfolioItem> matches, int size) {
        List<ItemView> views = matches.stream().map(ItemView::from).toList();
        return new PagedModel<>(new PageImpl<>(views, PageRequest.of(0, size), views.size()));
    }

    private ResponseEntity<ItemView> respond(PortfolioItem item) {
        return ResponseEntity.ok().eTag(EntityTags.of(item.version())).body(ItemView.from(item));
    }
}
