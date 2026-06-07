package com.ClinicaDeYmid.billing_service.module.config.controller;

import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import com.ClinicaDeYmid.billing_service.module.config.service.TaxConfigurationService;
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
@RequestMapping("/api/v1/billing/taxes")
@RequiredArgsConstructor
@Tag(name = "Tax Configuration", description = "Configuración de impuestos aplicables a la facturación")
@SecurityRequirement(name = "Bearer Authentication")
public class TaxConfigurationController {

    private final TaxConfigurationService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar impuestos activos",
            description = "Retorna todos los impuestos activos ordenados por tipo y nombre. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de impuestos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<TaxConfigurationResponseDto>> findAll() {
        log.info("Consultando todos los impuestos activos");
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar impuestos por tipo",
            description = "Retorna impuestos activos filtrados por tipo (IVA, INC, RETEFUENTE, RETEIVA). Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impuestos del tipo indicado"),
            @ApiResponse(responseCode = "400", description = "Tipo de impuesto inválido"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<TaxConfigurationResponseDto>> findByType(@PathVariable TaxType type) {
        log.info("Consultando impuestos de tipo: {}", type);
        return ResponseEntity.ok(service.findByType(type));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener impuesto por ID",
            description = "Retorna un impuesto específico por su ID. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impuesto encontrado"),
            @ApiResponse(responseCode = "404", description = "Impuesto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<TaxConfigurationResponseDto> findById(@PathVariable @Min(1) Long id) {
        log.info("Consultando impuesto ID: {}", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear impuesto",
            description = "Crea un nuevo tipo de impuesto. El código debe ser único (estándar DIAN). Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Impuesto creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe un impuesto con ese código"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<TaxConfigurationResponseDto> create(
            @Valid @RequestBody TaxConfigurationRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creando impuesto con código: {}", dto.code());
        TaxConfigurationResponseDto created = service.create(dto);

        URI location = uriBuilder.path("/api/v1/billing/taxes/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar impuesto",
            description = "Actualiza un impuesto existente. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impuesto actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Impuesto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Código ya en uso por otro impuesto"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<TaxConfigurationResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody TaxConfigurationRequestDto dto) {

        log.info("Actualizando impuesto ID: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activar / desactivar impuesto",
            description = "Alterna el estado activo del impuesto. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado"),
            @ApiResponse(responseCode = "404", description = "Impuesto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<TaxConfigurationResponseDto> toggleActive(@PathVariable @Min(1) Long id) {
        log.info("Alternando estado activo del impuesto ID: {}", id);
        return ResponseEntity.ok(service.toggleActive(id));
    }
}
