package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.BedCommands;
import com.ClinicaDeYmid.admissions_service.application.BedQueries;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.Room;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.BedResponses.BedView;
import com.ClinicaDeYmid.admissions_service.infrastructure.web.BedResponses.RoomView;
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
@RequestMapping(BedController.BASE_PATH)
@Tag(name = "Camas", description = "Habitaciones, camas y su ocupación")
class BedController {

    static final String BASE_PATH = "/api/v1/admissions";

    private final BedCommands commands;
    private final BedQueries queries;

    BedController(BedCommands commands, BedQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/rooms")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Abrir una habitación en una ubicación")
    ResponseEntity<RoomView> openRoom(@Valid @RequestBody BedRequests.RoomOpening request) {
        Room room = commands.openRoom(request.name(), request.locationUuid());
        return ResponseEntity.created(URI.create(BASE_PATH + "/rooms/" + room.uuid()))
                .eTag(EntityTags.of(room.version()))
                .body(RoomView.from(room));
    }

    @GetMapping("/locations/{uuid}/rooms")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las habitaciones de una ubicación")
    List<RoomView> rooms(@PathVariable UUID uuid) {
        return queries.roomsOf(uuid).stream().map(RoomView::from).toList();
    }

    @GetMapping("/rooms/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una habitación")
    ResponseEntity<RoomView> room(@PathVariable UUID uuid) {
        Room room = queries.room(uuid);
        return ResponseEntity.ok().eTag(EntityTags.of(room.version())).body(RoomView.from(room));
    }

    @PostMapping("/rooms/{uuid}/retirement")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Retirar una habitación sin borrarla")
    ResponseEntity<RoomView> retireRoom(@PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @Valid @RequestBody BedRequests.Reason request) {
        Room room = commands.retireRoom(uuid, EntityTags.requiredVersion(ifMatch), request.reason());
        return ResponseEntity.ok().eTag(EntityTags.of(room.version())).body(RoomView.from(room));
    }

    @PostMapping("/rooms/{uuid}/restoration")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Volver a usar una habitación retirada")
    ResponseEntity<RoomView> restoreRoom(@PathVariable UUID uuid,
                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Room room = commands.restoreRoom(uuid, EntityTags.requiredVersion(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(room.version())).body(RoomView.from(room));
    }

    @PostMapping("/beds")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Instalar una cama en una habitación")
    ResponseEntity<BedView> installBed(@Valid @RequestBody BedRequests.BedInstallation request) {
        Bed bed = commands.installBed(request.label(), request.roomUuid());
        return ResponseEntity.created(URI.create(BASE_PATH + "/beds/" + bed.uuid()))
                .eTag(EntityTags.of(bed.version()))
                .body(BedView.from(bed));
    }

    @GetMapping("/rooms/{uuid}/beds")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las camas de una habitación")
    List<BedView> beds(@PathVariable UUID uuid) {
        return queries.bedsOf(uuid).stream().map(BedView::from).toList();
    }

    @GetMapping("/locations/{uuid}/available-beds")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Listar las camas libres de una ubicación")
    List<BedView> availableBeds(@PathVariable UUID uuid) {
        return queries.availableIn(uuid).stream().map(BedView::from).toList();
    }

    @GetMapping("/beds/{uuid}")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Consultar una cama y su estado")
    ResponseEntity<BedView> bed(@PathVariable UUID uuid) {
        return tagged(queries.bed(uuid));
    }

    @PostMapping("/beds/{uuid}/occupancy")
    @PreAuthorize(Access.MOVE_BED)
    @Operation(summary = "Ocupar una cama",
            description = "La base de datos impide que dos estancias se solapen en la misma cama")
    ResponseEntity<BedView> occupy(@PathVariable UUID uuid, @Valid @RequestBody BedRequests.Occupancy request) {
        return tagged(commands.occupy(uuid, request.occupantUuid()));
    }

    @PostMapping("/beds/{uuid}/release")
    @PreAuthorize(Access.MOVE_BED)
    @Operation(summary = "Liberar una cama, que queda en limpieza")
    ResponseEntity<BedView> release(@PathVariable UUID uuid) {
        return tagged(commands.release(uuid));
    }

    @PostMapping("/beds/{uuid}/cleaning-completion")
    @PreAuthorize(Access.MOVE_BED)
    @Operation(summary = "Dar por terminada la limpieza y dejar la cama disponible")
    ResponseEntity<BedView> finishCleaning(@PathVariable UUID uuid,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(commands.finishCleaning(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/beds/{uuid}/maintenance")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Mandar una cama a mantenimiento")
    ResponseEntity<BedView> sendToMaintenance(@PathVariable UUID uuid,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody BedRequests.Reason request) {
        return tagged(commands.sendToMaintenance(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/beds/{uuid}/block")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Bloquear una cama con un motivo")
    ResponseEntity<BedView> block(@PathVariable UUID uuid,
                                  @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                  @Valid @RequestBody BedRequests.Reason request) {
        return tagged(commands.block(uuid, EntityTags.requiredVersion(ifMatch), request.reason()));
    }

    @PostMapping("/beds/{uuid}/return-to-service")
    @PreAuthorize(Access.MANAGE_BEDS)
    @Operation(summary = "Devolver al servicio una cama en mantenimiento o bloqueada")
    ResponseEntity<BedView> returnToService(@PathVariable UUID uuid,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return tagged(commands.returnToService(uuid, EntityTags.requiredVersion(ifMatch)));
    }

    private ResponseEntity<BedView> tagged(Bed bed) {
        return ResponseEntity.ok().eTag(EntityTags.of(bed.version())).body(BedView.from(bed));
    }
}
