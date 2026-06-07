package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PriceManualItemResponseDto(

        @Schema(description = "ID del ítem")
        Long id,

        @Schema(description = "ID del manual al que pertenece")
        Long priceManualId,

        @Schema(description = "ID del portafolio de clients-service")
        Long portfolioId,

        @Schema(description = "Código CUPS")
        String codeCups,

        @Schema(description = "Código clínico interno")
        String codeClinic,

        @Schema(description = "Descripción del servicio")
        String description,

        @Schema(description = "Precio base")
        BigDecimal basePrice,

        @Schema(description = "Unidad de cobro")
        String unit,

        @Schema(description = "Estado activo")
        Boolean active
) {}
