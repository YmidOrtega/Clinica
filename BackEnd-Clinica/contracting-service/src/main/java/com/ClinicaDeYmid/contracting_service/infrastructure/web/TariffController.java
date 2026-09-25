package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.commons.security.CurrentUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.application.TariffCommands;
import com.ClinicaDeYmid.contracting_service.application.TariffQueries;
import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.TariffResponses.ItemView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.TariffResponses.SurgicalRulesView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.TariffResponses.LoadView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.TariffResponses.ManualView;
import com.ClinicaDeYmid.contracting_service.infrastructure.web.TariffResponses.VersionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping(TariffController.BASE_PATH)
@Tag(name = "Tariff manuals", description = "Manuales tarifarios versionados: ISS, SOAT o propios de la clínica")
class TariffController {

    static final String BASE_PATH = "/api/v1/tariff-manuals";

    private final TariffCommands commands;
    private final TariffQueries queries;
    private final RecentAuthentication recentAuthentication;
    private final CurrentUser currentUser;

    TariffController(TariffCommands commands, TariffQueries queries, RecentAuthentication recentAuthentication,
                     CurrentUser currentUser) {
        this.commands = commands;
        this.queries = queries;
        this.recentAuthentication = recentAuthentication;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize(Access.MANAGE_TARIFFS)
    @Operation(summary = "Registrar un manual tarifario y su unidad de precio")
    ResponseEntity<ManualView> registerManual(@RequestBody TariffRequests.Manual request) {
        TariffManual manual = commands.registerManual(request.code(), request.name(), request.unit());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + manual.uuid()))
                .eTag(EntityTags.of(manual.version()))
                .body(ManualView.from(manual));
    }

    @GetMapping
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los manuales tarifarios")
    List<ManualView> manuals() {
        return queries.manuals().stream().map(ManualView::from).toList();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un manual tarifario")
    ResponseEntity<ManualView> manual(@PathVariable UUID uuid) {
        TariffManual manual = queries.manual(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(manual.version())).body(ManualView.from(manual));
    }

    @PostMapping("/{uuid}/versions")
    @PreAuthorize(Access.MANAGE_TARIFFS)
    @Operation(summary = "Abrir una versión del manual como borrador",
            description = "El valor de la unidad convierte las tarifas a pesos: 1 para manuales en pesos, el salario mínimo diario del año para los expresados en SMLDV")
    ResponseEntity<VersionView> draftVersion(@PathVariable UUID uuid, @RequestBody TariffRequests.Version request) {
        TariffManualVersion version = commands.draftVersion(uuid, request.label(), request.unitValue(), request.validFrom());
        return ResponseEntity.created(URI.create(BASE_PATH + "/versions/" + version.uuid()))
                .eTag(EntityTags.of(version.version()))
                .body(VersionView.from(version));
    }

    @GetMapping("/{uuid}/versions")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las versiones de un manual")
    List<VersionView> versions(@PathVariable UUID uuid) {
        return queries.versionsOf(uuid).stream().map(VersionView::from).toList();
    }

    @GetMapping("/{uuid}/versions/in-force")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar la versión vigente del manual en una fecha")
    VersionView versionInForce(@PathVariable UUID uuid,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return VersionView.from(queries.versionInForceOn(uuid, on));
    }

    @PostMapping("/versions/{versionUuid}/items")
    @PreAuthorize(Access.MANAGE_TARIFFS)
    @Operation(summary = "Cargar las tarifas de una versión",
            description = "Idempotente por huella SHA-256 del contenido: repetir la misma carga no cambia nada")
    LoadView load(@PathVariable UUID versionUuid, @Valid @RequestBody TariffRequests.Load request) {
        return LoadView.from(commands.load(versionUuid, request.toEntries()));
    }

    @GetMapping("/versions/{versionUuid}/items")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las tarifas de una versión, con su valor en pesos")
    List<ItemView> items(@PathVariable UUID versionUuid,
                         @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
                         @RequestParam(defaultValue = "50") @Min(value = 1, message = "debe ser al menos 1")
                         @Max(value = 200, message = "no puede superar 200") int size) {
        return queries.items(versionUuid, page, size).stream().map(ItemView::from).toList();
    }

    @GetMapping("/versions/{versionUuid}/items/{cupsCode}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar la tarifa de un código CUPS en una versión")
    ItemView item(@PathVariable UUID versionUuid, @PathVariable String cupsCode) {
        return ItemView.from(queries.item(versionUuid, cupsCode));
    }

    @PostMapping("/versions/{versionUuid}/surgical-rules")
    @PreAuthorize(Access.MANAGE_TARIFFS)
    @Operation(summary = "Cargar las reglas de liquidación quirúrgica de una versión en borrador",
            description = "Una regla por componente: tasa por unidad de la base o tabla por rangos, base mínima y "
                    + "porcentajes para procedimientos adicionales por la misma vía o por otra. Idempotente por huella")
    SurgicalRulesView loadSurgicalRules(@PathVariable UUID versionUuid,
                                        @Valid @RequestBody TariffRequests.SurgicalRules request) {
        return SurgicalRulesView.from(commands.loadSurgicalRules(versionUuid, request.basis(), request.toDefinitions(),
                currentUser.get().map(user -> user.uuid().toString()).orElse(null)));
    }

    @GetMapping("/versions/{versionUuid}/surgical-rules")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar las reglas de liquidación quirúrgica de una versión")
    SurgicalRulesView surgicalRules(@PathVariable UUID versionUuid) {
        return SurgicalRulesView.from(queries.surgicalRules(versionUuid));
    }

    @PostMapping("/versions/{versionUuid}/activation")
    @PreAuthorize(Access.MANAGE_TARIFFS)
    @Operation(summary = "Publicar la versión y retirar la anterior",
            description = "Exige segundo factor verificado en los últimos 5 minutos: a partir de aquí las tarifas ya no se pueden cambiar")
    ResponseEntity<VersionView> activate(@PathVariable UUID versionUuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        recentAuthentication.require();
        TariffManualVersion version = commands.activate(versionUuid, EntityTags.requiredVersion(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(version.version())).body(VersionView.from(version));
    }
}
