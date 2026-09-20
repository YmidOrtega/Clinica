package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.application.CatalogueQueries;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.CatalogueResponses.CareTypeView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.CatalogueResponses.ConfigurationView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.CatalogueResponses.LocationView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.CatalogueResponses.ServiceTypeView;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(CatalogueController.BASE_PATH)
@Tag(name = "Catálogos", description = "Tipos de servicio, tipos de atención, ubicaciones y servicios configurados")
class CatalogueController {

    static final String BASE_PATH = "/api/v1/admissions/catalogue";

    private final CatalogueCommands commands;
    private final CatalogueQueries queries;

    CatalogueController(CatalogueCommands commands, CatalogueQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/service-types")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Definir un tipo de servicio")
    ResponseEntity<ServiceTypeView> defineServiceType(@Valid @RequestBody CatalogueRequests.ServiceTypeDefinition request) {
        ServiceType defined = commands.defineServiceType(request.name(), request.kind());
        return created("/service-types/", defined.uuid(), defined.version(), ServiceTypeView.from(defined));
    }

    @GetMapping("/service-types")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los tipos de servicio")
    List<ServiceTypeView> serviceTypes() {
        return queries.serviceTypes().stream().map(ServiceTypeView::from).toList();
    }

    @GetMapping("/service-types/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un tipo de servicio")
    ResponseEntity<ServiceTypeView> serviceType(@PathVariable UUID uuid) {
        ServiceType serviceType = queries.serviceType(uuid);
        return tagged(serviceType.version(), ServiceTypeView.from(serviceType));
    }

    @PutMapping("/service-types/{uuid}")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Corregir el nombre de un tipo de servicio")
    ResponseEntity<ServiceTypeView> renameServiceType(@PathVariable UUID uuid,
                                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                      @Valid @RequestBody CatalogueRequests.Rename request) {
        ServiceType renamed = commands.renameServiceType(request.name(), uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(renamed.version(), ServiceTypeView.from(renamed));
    }

    @PostMapping("/service-types/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Retirar un tipo de servicio sin borrarlo")
    ResponseEntity<ServiceTypeView> retireServiceType(@PathVariable UUID uuid,
                                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                      @Valid @RequestBody CatalogueRequests.Retirement request) {
        ServiceType retired = commands.retireServiceType(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(retired.version(), ServiceTypeView.from(retired));
    }

    @PostMapping("/service-types/{uuid}/restoration")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Volver a usar un tipo de servicio retirado")
    ResponseEntity<ServiceTypeView> restoreServiceType(@PathVariable UUID uuid,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ServiceType restored = commands.restoreServiceType(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(restored.version(), ServiceTypeView.from(restored));
    }

    @PostMapping("/care-types")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Definir un tipo de atención dentro de un tipo de servicio")
    ResponseEntity<CareTypeView> defineCareType(@Valid @RequestBody CatalogueRequests.CareTypeDefinition request) {
        CareType defined = commands.defineCareType(request.name(), request.serviceTypeUuid());
        return created("/care-types/", defined.uuid(), defined.version(), CareTypeView.from(defined));
    }

    @GetMapping("/service-types/{uuid}/care-types")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los tipos de atención de un tipo de servicio")
    List<CareTypeView> careTypes(@PathVariable UUID uuid) {
        return queries.careTypesOf(uuid).stream().map(CareTypeView::from).toList();
    }

    @PostMapping("/care-types/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Retirar un tipo de atención")
    ResponseEntity<CareTypeView> retireCareType(@PathVariable UUID uuid,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @Valid @RequestBody CatalogueRequests.Retirement request) {
        CareType retired = commands.retireCareType(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(retired.version(), CareTypeView.from(retired));
    }

    @PostMapping("/care-types/{uuid}/restoration")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Volver a usar un tipo de atención retirado")
    ResponseEntity<CareTypeView> restoreCareType(@PathVariable UUID uuid,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        CareType restored = commands.restoreCareType(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(restored.version(), CareTypeView.from(restored));
    }

    @PostMapping("/locations")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Definir una ubicación")
    ResponseEntity<LocationView> defineLocation(@Valid @RequestBody CatalogueRequests.Rename request) {
        Location defined = commands.defineLocation(request.name());
        return created("/locations/", defined.uuid(), defined.version(), LocationView.from(defined));
    }

    @GetMapping("/locations")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las ubicaciones")
    List<LocationView> locations() {
        return queries.locations().stream().map(LocationView::from).toList();
    }

    @GetMapping("/locations/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una ubicación")
    ResponseEntity<LocationView> location(@PathVariable UUID uuid) {
        Location location = queries.location(uuid);
        return tagged(location.version(), LocationView.from(location));
    }

    @PutMapping("/locations/{uuid}")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Corregir el nombre de una ubicación")
    ResponseEntity<LocationView> renameLocation(@PathVariable UUID uuid,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @Valid @RequestBody CatalogueRequests.Rename request) {
        Location renamed = commands.renameLocation(request.name(), uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(renamed.version(), LocationView.from(renamed));
    }

    @PostMapping("/locations/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Retirar una ubicación sin borrarla")
    ResponseEntity<LocationView> retireLocation(@PathVariable UUID uuid,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @Valid @RequestBody CatalogueRequests.Retirement request) {
        Location retired = commands.retireLocation(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(retired.version(), LocationView.from(retired));
    }

    @PostMapping("/locations/{uuid}/restoration")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Volver a usar una ubicación retirada")
    ResponseEntity<LocationView> restoreLocation(@PathVariable UUID uuid,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Location restored = commands.restoreLocation(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(restored.version(), LocationView.from(restored));
    }

    @PostMapping("/configured-services")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Configurar un tipo de servicio en una ubicación")
    ResponseEntity<ConfigurationView> configure(@Valid @RequestBody CatalogueRequests.Configuration request) {
        ConfigurationService configured = commands.configure(request.serviceTypeUuid(), request.locationUuid());
        return created("/configured-services/", configured.uuid(), configured.version(),
                ConfigurationView.from(configured));
    }

    @GetMapping("/configured-services")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar los servicios configurados")
    List<ConfigurationView> configurations() {
        return queries.configurations().stream().map(ConfigurationView::from).toList();
    }

    @GetMapping("/configured-services/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar un servicio configurado")
    ResponseEntity<ConfigurationView> configuration(@PathVariable UUID uuid) {
        ConfigurationService configured = queries.configuration(uuid);
        return tagged(configured.version(), ConfigurationView.from(configured));
    }

    @PostMapping("/configured-services/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Retirar un servicio configurado")
    ResponseEntity<ConfigurationView> retireConfiguration(@PathVariable UUID uuid,
                                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                          @Valid @RequestBody CatalogueRequests.Retirement request) {
        ConfigurationService retired =
                commands.retireConfiguration(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return tagged(retired.version(), ConfigurationView.from(retired));
    }

    @PostMapping("/configured-services/{uuid}/restoration")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Volver a usar un servicio configurado retirado")
    ResponseEntity<ConfigurationView> restoreConfiguration(@PathVariable UUID uuid,
                                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ConfigurationService restored = commands.restoreConfiguration(uuid, EntityTags.requiredVersion(ifMatch));
        return tagged(restored.version(), ConfigurationView.from(restored));
    }

    private <T> ResponseEntity<T> created(String path, UUID uuid, long version, T body) {
        return ResponseEntity.created(URI.create(BASE_PATH + path + uuid))
                .eTag(EntityTags.of(version))
                .body(body);
    }

    private <T> ResponseEntity<T> tagged(long version, T body) {
        return ResponseEntity.ok().eTag(EntityTags.of(version)).body(body);
    }
}
