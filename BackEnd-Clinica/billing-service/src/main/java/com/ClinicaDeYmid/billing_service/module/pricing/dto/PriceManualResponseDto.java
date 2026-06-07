package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PriceManualResponseDto(

        @Schema(description = "ID del manual")
        Long id,

        @Schema(description = "Nombre del manual")
        String name,

        @Schema(description = "Código único del manual")
        String code,

        @Schema(description = "Tipo de manual")
        PriceManualType type,

        @Schema(description = "Año de vigencia")
        Short year,

        @Schema(description = "Descripción del manual")
        String description,

        @Schema(description = "Total de ítems registrados en el manual")
        int itemCount,

        @Schema(description = "Estado activo")
        Boolean active,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Usuario que creó el manual")
        String createdBy
) {}
