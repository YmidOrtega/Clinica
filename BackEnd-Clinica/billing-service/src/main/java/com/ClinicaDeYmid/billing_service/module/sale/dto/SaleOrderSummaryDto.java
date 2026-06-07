package com.ClinicaDeYmid.billing_service.module.sale.dto;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleOrderSummaryDto(

        @Schema(description = "ID de la orden")
        Long id,

        @Schema(description = "ID de la atención")
        Long attentionId,

        @Schema(description = "ID del paciente")
        String patientId,

        @Schema(description = "NIT del prestador")
        String healthProviderNit,

        @Schema(description = "Estado")
        SaleOrderStatus status,

        @Schema(description = "Total con impuestos")
        BigDecimal totalAmount,

        @Schema(description = "Neto a cobrar")
        BigDecimal netAmount,

        @Schema(description = "Número de ítems")
        int itemCount,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt
) {}
