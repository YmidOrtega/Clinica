package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.commons.security.CurrentUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.application.ContractCommands;
import com.ClinicaDeYmid.contracting_service.application.ContractQueries;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.ContractResponses.ContractView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.ContractResponses.ExceptionView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.ContractResponses.PackageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ContractController.BASE_PATH)
@Tag(name = "Contracts", description = "Contratos con cada pagador: vigencia, modalidad, manual tarifario, excepciones y paquetes")
class ContractController {

    static final String BASE_PATH = "/api/v1/contracts";

    private final ContractCommands commands;
    private final ContractQueries queries;
    private final RecentAuthentication recentAuthentication;
    private final CurrentUser currentUser;

    ContractController(ContractCommands commands, ContractQueries queries, RecentAuthentication recentAuthentication,
                       CurrentUser currentUser) {
        this.commands = commands;
        this.queries = queries;
        this.recentAuthentication = recentAuthentication;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Abrir un contrato como borrador")
    ResponseEntity<ContractView> draft(@Valid @RequestBody ContractRequests.Draft request) {
        Contract contract = commands.draft(request.payerUuid(), request.number(), request.name(), request.modality(),
                request.validFrom(), request.validTo());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + contract.uuid()))
                .eTag(EntityTags.of(contract.version()))
                .body(ContractView.from(contract));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un contrato")
    ResponseEntity<ContractView> get(@PathVariable UUID uuid) {
        return respond(queries.get(uuid));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los contratos de un pagador, o solo los vigentes en una fecha")
    List<ContractView> ofPayer(@RequestParam UUID payer,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inForceOn) {
        List<Contract> found = inForceOn == null ? queries.ofPayer(payer) : queries.inForceOn(payer, inForceOn);
        return found.stream().map(ContractView::from).toList();
    }

    @PutMapping("/{uuid}/tariff-terms")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Pactar el manual tarifario y el factor del contrato",
            description = "Solo mientras el contrato es un borrador; exige segundo factor reciente porque define los precios")
    ResponseEntity<ContractView> agreeTariff(@PathVariable UUID uuid,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @RequestBody ContractRequests.TariffTerms request) {
        recentAuthentication.require();
        return respond(commands.agreeTariff(uuid, EntityTags.requiredVersion(ifMatch), request.tariffVersionUuid(),
                request.factor()));
    }

    @PutMapping("/{uuid}/name")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Corregir el nombre del contrato")
    ResponseEntity<ContractView> rename(@PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @RequestBody ContractRequests.Rename request) {
        return respond(commands.rename(uuid, EntityTags.requiredVersion(ifMatch), request.name()));
    }

    @PutMapping("/{uuid}/validity")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Prorrogar o cerrar la vigencia del contrato")
    ResponseEntity<ContractView> extend(@PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @RequestBody ContractRequests.Validity request) {
        return respond(commands.extendTo(uuid, EntityTags.requiredVersion(ifMatch), request.validTo()));
    }

    @PostMapping("/{uuid}/activation")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Activar el contrato", description = "Exige segundo factor verificado en los últimos 5 minutos")
    ResponseEntity<ContractView> activate(@PathVariable UUID uuid,
                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        recentAuthentication.require();
        return respond(commands.activate(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/suspension")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Suspender el contrato")
    ResponseEntity<ContractView> suspend(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @RequestBody ContractRequests.StatusChange request) {
        return respond(commands.suspend(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/termination")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Terminar el contrato; sus facturas y tarifas quedan como están")
    ResponseEntity<ContractView> terminate(@PathVariable UUID uuid,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                           @RequestBody ContractRequests.StatusChange request) {
        return respond(commands.terminate(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/{uuid}/tariff-exceptions")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Pactar el precio de un servicio por fuera del manual",
            description = "Exige segundo factor reciente; la excepción anterior del mismo código se revoca en esa fecha")
    ExceptionView registerException(@PathVariable UUID uuid, @RequestBody ContractRequests.TariffExceptionRequest request) {
        recentAuthentication.require();
        return ExceptionView.from(commands.registerException(uuid, request.cupsCode(), request.agreedPrice(),
                request.reason(), request.validFrom(), actor()));
    }

    @GetMapping("/{uuid}/tariff-exceptions")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las excepciones de precio del contrato, vigentes y revocadas")
    List<ExceptionView> exceptions(@PathVariable UUID uuid) {
        return queries.exceptionsOf(uuid).stream().map(ExceptionView::from).toList();
    }

    @PostMapping("/tariff-exceptions/{exceptionUuid}/revocation")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Revocar una excepción de precio desde una fecha")
    ExceptionView revokeException(@PathVariable UUID exceptionUuid, @RequestBody ContractRequests.Revocation request) {
        recentAuthentication.require();
        return ExceptionView.from(commands.revokeException(exceptionUuid, request.from(), request.reason(), actor()));
    }

    @PostMapping("/{uuid}/packages")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Pactar un paquete: precio único por un conjunto de servicios",
            description = "Lo que no esté incluido se factura por evento; exige segundo factor reciente")
    PackageView agreePackage(@PathVariable UUID uuid, @RequestBody ContractRequests.PackageRequest request) {
        recentAuthentication.require();
        return PackageView.from(commands.agreePackage(uuid, request.code(), request.name(), request.price(),
                request.includedCodes(), request.validFrom(), actor()));
    }

    @GetMapping("/{uuid}/packages")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los paquetes del contrato")
    List<PackageView> packages(@PathVariable UUID uuid) {
        return queries.packagesOf(uuid).stream().map(PackageView::from).toList();
    }

    @PostMapping("/packages/{packageUuid}/revocation")
    @PreAuthorize(Access.MANAGE_CONTRACTS)
    @Operation(summary = "Revocar un paquete desde una fecha")
    PackageView revokePackage(@PathVariable UUID packageUuid, @RequestBody ContractRequests.Revocation request) {
        recentAuthentication.require();
        return PackageView.from(commands.revokePackage(packageUuid, request.from(), actor()));
    }

    private String actor() {
        return currentUser.get().map(user -> user.uuid().toString()).orElse(null);
    }

    private ResponseEntity<ContractView> respond(Contract contract) {
        return ResponseEntity.ok().eTag(EntityTags.of(contract.version())).body(ContractView.from(contract));
    }
}
