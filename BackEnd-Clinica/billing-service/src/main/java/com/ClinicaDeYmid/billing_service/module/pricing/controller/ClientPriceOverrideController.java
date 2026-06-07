package com.ClinicaDeYmid.billing_service.module.pricing.controller;

import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.service.ClientPriceOverrideService;
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
@RequestMapping("/api/v1/billing/overrides")
@RequiredArgsConstructor
@Tag(name = "Client Price Overrides", description = "Precios negociados por contrato y servicio del portafolio")
@SecurityRequirement(name = "Bearer Authentication")
public class ClientPriceOverrideController {

    private final ClientPriceOverrideService service;

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar overrides activos por contrato",
            description = "Retorna todos los overrides de precio activos para un contrato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de overrides"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<ClientPriceOverrideResponseDto>> findByContract(
            @PathVariable @Min(1) Long contractId) {
        log.info("Consultando overrides del contrato ID: {}", contractId);
        return ResponseEntity.ok(service.findByContract(contractId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Obtener override por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Override encontrado"),
            @ApiResponse(responseCode = "404", description = "Override no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<ClientPriceOverrideResponseDto> findById(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Crear override de precio",
            description = "Registra un precio negociado para un contrato y portafolio. "
                    + "Si ya existe uno activo para la misma combinación, lo desactiva automáticamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Override creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<ClientPriceOverrideResponseDto> create(
            @Valid @RequestBody ClientPriceOverrideRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creando override para contrato: {} portafolio: {}", dto.contractId(), dto.portfolioId());
        ClientPriceOverrideResponseDto created = service.create(dto);
        URI location = uriBuilder.path("/api/v1/billing/overrides/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Actualizar override de precio",
            description = "Actualiza los datos de un override existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Override actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Override no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<ClientPriceOverrideResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ClientPriceOverrideRequestDto dto) {

        log.info("Actualizando override ID: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Desactivar override",
            description = "Desactiva un override de precio. El sistema volverá al precio del manual o portafolio base.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Override desactivado"),
            @ApiResponse(responseCode = "404", description = "Override no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Void> deactivate(@PathVariable @Min(1) Long id) {
        log.info("Desactivando override ID: {}", id);
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
