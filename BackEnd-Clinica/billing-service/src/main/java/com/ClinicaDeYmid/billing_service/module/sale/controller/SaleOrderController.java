package com.ClinicaDeYmid.billing_service.module.sale.controller;

import com.ClinicaDeYmid.billing_service.module.sale.dto.*;
import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import com.ClinicaDeYmid.billing_service.module.sale.service.SaleOrderItemService;
import com.ClinicaDeYmid.billing_service.module.sale.service.SaleOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/billing/sales")
@RequiredArgsConstructor
@Tag(name = "Sale Orders", description = "Ventas y cuentas de cobro generadas a partir de atenciones")
@SecurityRequirement(name = "Bearer Authentication")
public class SaleOrderController {

    private final SaleOrderService saleOrderService;
    private final SaleOrderItemService itemService;

    // ── Órdenes ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Obtener orden por ID",
            description = "Retorna la orden completa con todos sus ítems.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden encontrada"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> findById(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(saleOrderService.findById(id));
    }

    @GetMapping("/attention/{attentionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Listar órdenes de una atención",
            description = "Retorna todas las órdenes vinculadas a una atención, ordenadas por fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de órdenes"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<SaleOrderSummaryDto>> findByAttention(
            @PathVariable @Min(1) Long attentionId) {
        return ResponseEntity.ok(saleOrderService.findByAttention(attentionId));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar órdenes de un paciente (paginado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Órdenes del paciente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Page<SaleOrderSummaryDto>> findByPatient(
            @PathVariable String patientId,
            Pageable pageable) {
        return ResponseEntity.ok(saleOrderService.findByPatient(patientId, pageable));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Listar órdenes por estado (paginado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Órdenes en el estado indicado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<Page<SaleOrderSummaryDto>> findByStatus(
            @PathVariable SaleOrderStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(saleOrderService.findByStatus(status, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Crear orden de venta",
            description = "Crea una orden en estado DRAFT para una atención. "
                    + "Solo puede existir un DRAFT por atención.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "La atención ya tiene un borrador activo"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> create(
            @Valid @RequestBody SaleOrderRequestDto dto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creando orden de venta para atención ID: {}", dto.attentionId());
        SaleOrderResponseDto created = saleOrderService.create(dto);
        URI location = uriBuilder.path("/api/v1/billing/sales/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Actualizar cabecera de la orden",
            description = "Actualiza los datos de la orden. Solo se permite en estado DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden actualizada"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "La orden no está en DRAFT"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaleOrderRequestDto dto) {

        log.info("Actualizando orden ID: {}", id);
        return ResponseEntity.ok(saleOrderService.update(id, dto));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Confirmar orden de venta",
            description = "Cambia el estado de DRAFT a CONFIRMED y notifica a admissions-service "
                    + "para marcar la atención como facturada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden confirmada"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "La orden no puede confirmarse (no está en DRAFT o no tiene ítems)"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> confirm(@PathVariable @Min(1) Long id) {
        log.info("Confirmando orden ID: {}", id);
        return ResponseEntity.ok(saleOrderService.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Cancelar orden de venta",
            description = "Cancela una orden. No se puede cancelar si está en estado INVOICED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden cancelada"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "La orden está en INVOICED y no puede cancelarse"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> cancel(@PathVariable @Min(1) Long id) {
        log.info("Cancelando orden ID: {}", id);
        return ResponseEntity.ok(saleOrderService.cancel(id));
    }

    @PatchMapping("/{id}/copayment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Establecer copago del paciente",
            description = "Actualiza el copago y recalcula el neto a cobrar a la aseguradora. Solo en DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Copago actualizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "La orden no está en DRAFT"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> setCopayment(
            @PathVariable @Min(1) Long id,
            @RequestParam @DecimalMin(value = "0.00", message = "El copago no puede ser negativo.")
            BigDecimal amount) {

        log.info("Actualizando copago de orden ID: {} → {}", id, amount);
        return ResponseEntity.ok(saleOrderService.setCopayment(id, amount));
    }

    // ── Ítems ─────────────────────────────────────────────────────────────────

    @GetMapping("/{saleOrderId}/items")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Listar ítems de una orden")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítems de la orden"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<List<SaleOrderItemResponseDto>> findItems(
            @PathVariable @Min(1) Long saleOrderId) {
        return ResponseEntity.ok(itemService.findByOrder(saleOrderId));
    }

    @PostMapping("/{saleOrderId}/items")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Agregar ítem a la orden",
            description = "Agrega un servicio, examen, insumo u honorario. "
                    + "El precio unitario lo resuelve el servidor (override → manual → tarifa "
                    + "de contrato → precio base del portafolio) cuando el ítem trae portafolio "
                    + "o código CUPS; para conceptos manuales sin catálogo el precio es "
                    + "obligatorio en la petición. La tasa de impuesto se toma de la "
                    + "configuración. Calcula el subtotal y recalcula los totales. Solo en DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem agregado, totales actualizados"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "Orden no editable, portafolio duplicado, "
                    + "precio no resoluble o ítem manual sin precio"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> addItem(
            @PathVariable @Min(1) Long saleOrderId,
            @Valid @RequestBody SaleOrderItemRequestDto dto) {

        log.info("Agregando ítem a orden ID: {}", saleOrderId);
        return ResponseEntity.ok(itemService.addItem(saleOrderId, dto));
    }

    @PutMapping("/{saleOrderId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Actualizar ítem de la orden",
            description = "Vuelve a resolver el precio y la tasa de impuesto en el servidor, "
                    + "y recalcula el subtotal del ítem y los totales de la orden. Solo en DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem actualizado"),
            @ApiResponse(responseCode = "404", description = "Orden o ítem no encontrado"),
            @ApiResponse(responseCode = "409", description = "Orden no editable, precio no resoluble "
                    + "o ítem manual sin precio"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> updateItem(
            @PathVariable @Min(1) Long saleOrderId,
            @PathVariable @Min(1) Long itemId,
            @Valid @RequestBody SaleOrderItemRequestDto dto) {

        log.info("Actualizando ítem ID: {} de orden ID: {}", itemId, saleOrderId);
        return ResponseEntity.ok(itemService.updateItem(saleOrderId, itemId, dto));
    }

    @DeleteMapping("/{saleOrderId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Eliminar ítem de la orden",
            description = "Elimina el ítem y recalcula los totales. Solo en DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem eliminado, totales actualizados"),
            @ApiResponse(responseCode = "404", description = "Orden o ítem no encontrado"),
            @ApiResponse(responseCode = "409", description = "Orden no editable"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderResponseDto> removeItem(
            @PathVariable @Min(1) Long saleOrderId,
            @PathVariable @Min(1) Long itemId) {

        log.info("Eliminando ítem ID: {} de orden ID: {}", itemId, saleOrderId);
        return ResponseEntity.ok(itemService.removeItem(saleOrderId, itemId));
    }

    @PatchMapping("/{saleOrderId}/items/{itemId}/authorize")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Autorizar ítem de la orden",
            description = "Marca el ítem como autorizado por la aseguradora y registra el ID de autorización.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ítem autorizado"),
            @ApiResponse(responseCode = "404", description = "Ítem no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<SaleOrderItemResponseDto> authorizeItem(
            @PathVariable @Min(1) Long saleOrderId,
            @PathVariable @Min(1) Long itemId,
            @RequestParam @Min(1) Long authorizationId) {

        log.info("Autorizando ítem ID: {} con autorización ID: {}", itemId, authorizationId);
        return ResponseEntity.ok(itemService.authorizeItem(itemId, authorizationId));
    }
}
