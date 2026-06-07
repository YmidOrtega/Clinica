package com.ClinicaDeYmid.billing_service.module.pricing.controller;

import com.ClinicaDeYmid.billing_service.module.pricing.dto.*;
import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import com.ClinicaDeYmid.billing_service.module.pricing.service.PriceManualItemService;
import com.ClinicaDeYmid.billing_service.module.pricing.service.PriceManualService;
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
@RequestMapping("/api/v1/billing/manuals")
@RequiredArgsConstructor
@Tag(name = "Price Manuals", description = "Manuales de cobro y sus ítems por servicio/examen")
@SecurityRequirement(name = "Bearer Authentication")
public class PriceManualController {

    private final PriceManualService manualService;
    private final PriceManualItemService itemService;

    // ── Manuales ──────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar manuales activos",
            description = "Retorna todos los manuales de cobro activos ordenados por tipo y nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de manuales"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<PriceManualResponseDto>> findAll() {
        return ResponseEntity.ok(manualService.findAll());
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar manuales por tipo",
            description = "Retorna manuales activos filtrados por tipo (ISS, SOAT, PARTICULAR, CONTRATO, OTRO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manuales del tipo indicado"),
            @ApiResponse(responseCode = "400", description = "Tipo inválido"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<PriceManualResponseDto>> findByType(@PathVariable PriceManualType type) {
        return ResponseEntity.ok(manualService.findByType(type));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Obtener manual por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manual encontrado"),
            @ApiResponse(responseCode = "404", description = "Manual no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualResponseDto> findById(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(manualService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Crear manual de cobro",
            description = "Crea un nuevo manual de cobro. El código debe ser único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Manual creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Código ya en uso"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualResponseDto> create(
            @Valid @RequestBody PriceManualRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creando manual de cobro: {}", dto.code());
        PriceManualResponseDto created = manualService.create(dto);
        URI location = uriBuilder.path("/api/v1/billing/manuals/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Actualizar manual de cobro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manual actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Manual no encontrado"),
            @ApiResponse(responseCode = "409", description = "Código ya en uso"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody PriceManualRequestDto dto) {

        log.info("Actualizando manual de cobro ID: {}", id);
        return ResponseEntity.ok(manualService.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Activar / desactivar manual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado"),
            @ApiResponse(responseCode = "404", description = "Manual no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualResponseDto> toggle(@PathVariable @Min(1) Long id) {
        log.info("Alternando estado del manual ID: {}", id);
        return ResponseEntity.ok(manualService.toggleActive(id));
    }

    // ── Ítems del manual ──────────────────────────────────────────────────────

    @GetMapping("/{manualId}/items")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar ítems de un manual",
            description = "Retorna los ítems activos del manual indicado, ordenados por descripción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítems del manual"),
            @ApiResponse(responseCode = "404", description = "Manual no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<PriceManualItemResponseDto>> findItems(
            @PathVariable @Min(1) Long manualId) {
        return ResponseEntity.ok(itemService.findByManual(manualId));
    }

    @GetMapping("/{manualId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Obtener ítem por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem encontrado"),
            @ApiResponse(responseCode = "404", description = "Ítem no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualItemResponseDto> findItem(
            @PathVariable @Min(1) Long manualId,
            @PathVariable @Min(1) Long itemId) {
        return ResponseEntity.ok(itemService.findById(itemId));
    }

    @PostMapping("/{manualId}/items")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Agregar ítem al manual",
            description = "Agrega un servicio/examen al manual con su precio base.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ítem agregado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Manual no encontrado"),
            @ApiResponse(responseCode = "409", description = "El portafolio ya existe en el manual"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualItemResponseDto> addItem(
            @PathVariable @Min(1) Long manualId,
            @Valid @RequestBody PriceManualItemRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Agregando ítem al manual ID: {}", manualId);
        PriceManualItemResponseDto created = itemService.addToManual(manualId, dto);
        URI location = uriBuilder.path("/api/v1/billing/manuals/{manualId}/items/{itemId}")
                .buildAndExpand(manualId, created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{manualId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Actualizar ítem del manual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Ítem no encontrado"),
            @ApiResponse(responseCode = "409", description = "Portafolio ya en uso en el manual"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualItemResponseDto> updateItem(
            @PathVariable @Min(1) Long manualId,
            @PathVariable @Min(1) Long itemId,
            @Valid @RequestBody PriceManualItemRequestDto dto) {

        log.info("Actualizando ítem ID: {} del manual ID: {}", itemId, manualId);
        return ResponseEntity.ok(itemService.update(itemId, dto));
    }

    @PatchMapping("/{manualId}/items/{itemId}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Activar / desactivar ítem del manual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado"),
            @ApiResponse(responseCode = "404", description = "Ítem no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<PriceManualItemResponseDto> toggleItem(
            @PathVariable @Min(1) Long manualId,
            @PathVariable @Min(1) Long itemId) {
        return ResponseEntity.ok(itemService.toggleActive(itemId));
    }

    @DeleteMapping("/{manualId}/items/{itemId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Eliminar ítem del manual",
            description = "Elimina permanentemente un ítem del manual. Solo SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ítem eliminado"),
            @ApiResponse(responseCode = "404", description = "Ítem no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Void> removeItem(
            @PathVariable @Min(1) Long manualId,
            @PathVariable @Min(1) Long itemId) {

        log.info("Eliminando ítem ID: {} del manual ID: {}", itemId, manualId);
        itemService.remove(itemId);
        return ResponseEntity.noContent().build();
    }
}
