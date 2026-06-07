package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxConfigurationResponseDto(

        @Schema(description = "ID del impuesto")
        Long id,

        @Schema(description = "Nombre del impuesto")
        String name,

        @Schema(description = "Código DIAN del impuesto")
        String code,

        @Schema(description = "Tipo de impuesto")
        TaxType type,

        @Schema(description = "Porcentaje del impuesto")
        BigDecimal percentage,

        @Schema(description = "Aplica a servicios y procedimientos")
        Boolean appliesToServices,

        @Schema(description = "Aplica a medicamentos e insumos")
        Boolean appliesToMedications,

        @Schema(description = "Estado activo")
        Boolean active,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt
) {}
