package com.ClinicaDeYmid.billing_service.module.config.controller;

import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.service.BillingConfigurationService;
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

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/billing/configuration")
@RequiredArgsConstructor
@Tag(name = "Billing Configuration", description = "Configuración global del emisor de facturas electrónicas")
@SecurityRequirement(name = "Bearer Authentication")
public class BillingConfigurationController {

    private final BillingConfigurationService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener configuración activa",
            description = "Retorna la configuración de facturación activa. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe configuración activa"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<BillingConfigurationResponseDto> getActive() {
        log.info("Consultando configuración de facturación activa");
        return ResponseEntity.ok(service.getActive());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear configuración de facturación",
            description = "Crea la configuración del emisor. Solo puede existir una activa. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Configuración creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una configuración activa"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<BillingConfigurationResponseDto> create(
            @Valid @RequestBody BillingConfigurationRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creando configuración de facturación para NIT: {}", dto.clinicNit());
        BillingConfigurationResponseDto created = service.create(dto);

        URI location = uriBuilder.path("/api/v1/billing/configuration/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar configuración de facturación",
            description = "Actualiza los datos de la configuración existente. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Configuración no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<BillingConfigurationResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody BillingConfigurationRequestDto dto) {

        log.info("Actualizando configuración de facturación ID: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Desactivar configuración de facturación",
            description = "Desactiva la configuración activa. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Configuración desactivada"),
            @ApiResponse(responseCode = "404", description = "Configuración no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Void> deactivate(@PathVariable @Min(1) Long id) {
        log.info("Desactivando configuración de facturación ID: {}", id);
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
