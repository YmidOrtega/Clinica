package com.ClinicaDeYmid.billing_service.module.sale.dto;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleOrderResponseDto(

        @Schema(description = "ID de la orden de venta")
        Long id,

        @Schema(description = "ID de la atención")
        Long attentionId,

        @Schema(description = "ID del paciente (número de identificación)")
        String patientId,

        @Schema(description = "NIT del prestador de salud")
        String healthProviderNit,

        @Schema(description = "ID del contrato")
        Long contractId,

        @Schema(description = "ID del médico")
        Long doctorId,

        @Schema(description = "Estado de la orden")
        SaleOrderStatus status,

        @Schema(description = "Subtotal antes de impuestos")
        BigDecimal subtotal,

        @Schema(description = "Total de impuestos")
        BigDecimal taxAmount,

        @Schema(description = "Total con impuestos")
        BigDecimal totalAmount,

        @Schema(description = "Copago del paciente")
        BigDecimal copaymentAmount,

        @Schema(description = "Neto a cobrar al prestador (totalAmount - copaymentAmount)")
        BigDecimal netAmount,

        @Schema(description = "Observaciones")
        String notes,

        @Schema(description = "Ítems de la venta")
        List<SaleOrderItemResponseDto> items,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Usuario que creó la orden")
        String createdBy
) {}
