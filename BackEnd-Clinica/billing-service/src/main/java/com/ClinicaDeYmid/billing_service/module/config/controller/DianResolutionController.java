package com.ClinicaDeYmid.billing_service.module.config.controller;

import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import com.ClinicaDeYmid.billing_service.module.config.service.DianResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/billing/resolutions")
@RequiredArgsConstructor
@Tag(name = "DIAN Resolutions", description = "Resoluciones de numeración habilitadas por la DIAN")
@SecurityRequirement(name = "Bearer Authentication")
public class DianResolutionController {

    private final DianResolutionService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar resoluciones activas",
            description = "Retorna todas las resoluciones DIAN activas. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de resoluciones"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<DianResolutionResponseDto>> findAll() {
        log.info("Consultando todas las resoluciones DIAN activas");
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener resolución por ID",
            description = "Retorna una resolución DIAN específica. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resolución encontrada"),
            @ApiResponse(responseCode = "404", description = "Resolución no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<DianResolutionResponseDto> findById(@PathVariable @Min(1) Long id) {
        log.info("Consultando resolución DIAN ID: {}", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/valid/{documentType}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener resolución vigente por tipo de documento",
            description = "Retorna la resolución activa, vigente y con consecutivos disponibles para el tipo indicado. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resolución vigente encontrada"),
            @ApiResponse(responseCode = "404", description = "No hay resolución vigente para ese tipo"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<DianResolutionResponseDto> findValid(@PathVariable DocumentType documentType) {
        log.info("Consultando resolución vigente para tipo: {}", documentType);
        return ResponseEntity.ok(service.findValidForDocumentType(documentType));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Registrar resolución DIAN",
            description = "Registra una nueva resolución. Si ya existe una activa del mismo tipo de documento, la desactiva automáticamente. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Resolución creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una resolución con ese número"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<DianResolutionResponseDto> create(
            @Valid @RequestBody DianResolutionRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Registrando resolución DIAN número: {}", dto.resolutionNumber());
        DianResolutionResponseDto created = service.create(dto);

        URI location = uriBuilder.path("/api/v1/billing/resolutions/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar resolución DIAN",
            description = "Actualiza los datos de una resolución. No modifica el consecutivo actual. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resolución actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Resolución no encontrada"),
            @ApiResponse(responseCode = "409", description = "Número de resolución ya en uso"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<DianResolutionResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody DianResolutionRequestDto dto) {

        log.info("Actualizando resolución DIAN ID: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Desactivar resolución DIAN",
            description = "Desactiva una resolución. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Resolución desactivada"),
            @ApiResponse(responseCode = "404", description = "Resolución no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Void> deactivate(@PathVariable @Min(1) Long id) {
        log.info("Desactivando resolución DIAN ID: {}", id);
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
