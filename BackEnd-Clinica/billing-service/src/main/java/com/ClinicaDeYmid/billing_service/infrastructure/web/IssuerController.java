package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.IssuerCommands;
import com.ClinicaDeYmid.billing_service.application.IssuerQueries;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Nit;
import com.ClinicaDeYmid.billing_service.infrastructure.web.IssuerResponses.IssuerView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(IssuerController.BASE_PATH)
@Tag(name = "Emisor", description = "Datos de la clínica como facturador electrónico ante la DIAN")
class IssuerController {

    static final String BASE_PATH = "/api/v1/billing/issuer";

    private final IssuerCommands commands;
    private final IssuerQueries queries;

    IssuerController(IssuerCommands commands, IssuerQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Registrar a la clínica como emisor",
            description = "El NIT no se puede cambiar después; el emisor empieza en el ambiente de pruebas de la DIAN")
    ResponseEntity<IssuerView> configure(@Valid @RequestBody IssuerRequests.Configuration request) {
        Issuer issuer = commands.configure(new Nit(request.nit(), request.verificationDigit()),
                request.profile().toDomain());
        return ResponseEntity.created(URI.create(BASE_PATH)).eTag(EntityTags.of(issuer.version()))
                .body(IssuerView.from(issuer));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar los datos del emisor")
    ResponseEntity<IssuerView> issuer() {
        return tagged(queries.issuer());
    }

    @PutMapping
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Corregir los datos del emisor, salvo el NIT")
    ResponseEntity<IssuerView> revise(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                      @Valid @RequestBody IssuerRequests.Profile request) {
        return tagged(commands.revise(EntityTags.requiredVersion(ifMatch), request.toDomain()));
    }

    @PutMapping("/credit-note-prefix")
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Cambiar el prefijo de las notas crédito",
            description = "Cada prefijo lleva su propio consecutivo; la DIAN no exige resolución para notas")
    ResponseEntity<IssuerView> useCreditNotePrefix(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody IssuerRequests.CreditNotePrefix request) {
        return tagged(commands.useCreditNotePrefix(EntityTags.requiredVersion(ifMatch), request.prefix()));
    }

    @PostMapping("/production")
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Pasar a facturar en producción",
            description = "No tiene vuelta atrás y retira las resoluciones del ambiente de pruebas")
    ResponseEntity<IssuerView> goToProduction(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(commands.goToProduction(EntityTags.requiredVersion(ifMatch)));
    }

    private ResponseEntity<IssuerView> tagged(Issuer issuer) {
        return ResponseEntity.ok().eTag(EntityTags.of(issuer.version())).body(IssuerView.from(issuer));
    }
}
