package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.application.PayerCommands;
import com.ClinicaDeYmid.contracting_service.application.PayerQueries;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PayerResponses.PayerSummaryView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PayerResponses.PayerView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.PayerResponses.RevisionView;
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
@RequestMapping(PayerController.BASE_PATH)
@Tag(name = "Payers", description = "Pagadores: EPS, ARL, pólizas y demás contrapartes de un contrato")
class PayerController {

    static final String BASE_PATH = "/api/v1/payers";

    private final PayerCommands commands;
    private final PayerQueries queries;

    PayerController(PayerCommands commands, PayerQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Registrar un pagador")
    ResponseEntity<PayerView> register(@Valid @RequestBody PayerRequests.Register request) {
        Payer payer = commands.register(request.toDomain());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + payer.uuid()))
                .eTag(EntityTags.of(payer.version()))
                .body(PayerView.from(payer));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un pagador")
    ResponseEntity<PayerView> get(@PathVariable UUID uuid) {
        return respond(queries.get(uuid));
    }

    @PostMapping("/search")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Buscar por NIT exacto o por prefijo de razón social")
    PagedModel<PayerSummaryView> search(@Valid @RequestBody PayerRequests.Search request,
                                        @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
                                        @RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser al menos 1")
                                        @Max(value = 50, message = "no puede superar 50") int size) {
        boolean byNit = request.nit() != null;
        boolean bySocialReason = request.socialReason() != null;
        if (byNit == bySocialReason) {
            throw new ContractingException.InvalidData("search", "debe indicar nit o socialReason, pero no ambos");
        }
        if (byNit) {
            List<PayerSummaryView> matches = queries.findByNit(new Nit(request.nit()))
                    .map(PayerSummaryView::from)
                    .stream()
                    .toList();
            return new PagedModel<>(new PageImpl<>(matches, PageRequest.of(0, size), matches.size()));
        }
        PayerQueries.Page found = queries.searchBySocialReason(request.socialReason(), page, size);
        return new PagedModel<>(new PageImpl<>(found.matches().stream().map(PayerSummaryView::from).toList(),
                PageRequest.of(page, size), found.total()));
    }

    @PutMapping("/{uuid}/identity")
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Corregir razón social, NIT, tipo o código ADRES")
    ResponseEntity<PayerView> correctIdentity(@PathVariable UUID uuid,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @RequestBody PayerRequests.Identity request) {
        return respond(commands.correctIdentity(uuid, EntityTags.requiredVersion(ifMatch), request.toDomain()));
    }

    @PutMapping("/{uuid}/contact")
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Actualizar dirección, teléfono y correo de radicación")
    ResponseEntity<PayerView> updateContact(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @RequestBody PayerRequests.Contact request) {
        return respond(commands.updateContact(uuid, EntityTags.requiredVersion(ifMatch), request.toDomain()));
    }

    @PostMapping("/{uuid}/suspension")
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Suspender un pagador sin perder sus contratos")
    ResponseEntity<PayerView> suspend(@PathVariable UUID uuid,
                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                      @RequestBody PayerRequests.StatusChange request) {
        return respond(commands.suspend(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Reactivar un pagador suspendido o desactivado")
    ResponseEntity<PayerView> reactivate(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(commands.reactivate(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/deactivation")
    @PreAuthorize(Access.MANAGE_PAYERS)
    @Operation(summary = "Desactivar un pagador; nada se borra")
    ResponseEntity<PayerView> deactivate(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @RequestBody PayerRequests.StatusChange request) {
        return respond(commands.deactivate(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @GetMapping("/{uuid}/history")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar el historial de cambios del pagador")
    List<RevisionView> history(@PathVariable UUID uuid) {
        return queries.history(uuid).stream().map(RevisionView::from).toList();
    }

    private ResponseEntity<PayerView> respond(Payer payer) {
        return ResponseEntity.ok().eTag(EntityTags.of(payer.version())).body(PayerView.from(payer));
    }
}
