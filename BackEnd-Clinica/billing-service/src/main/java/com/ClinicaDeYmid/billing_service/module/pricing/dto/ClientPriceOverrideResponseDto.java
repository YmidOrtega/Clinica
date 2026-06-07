package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClientPriceOverrideResponseDto(

        @Schema(description = "ID del override")
        Long id,

        @Schema(description = "ID del contrato")
        Long contractId,

        @Schema(description = "NIT del prestador de salud")
        String healthProviderNit,

        @Schema(description = "ID del portafolio")
        Long portfolioId,

        @Schema(description = "Código CUPS del servicio")
        String codeCups,

        @Schema(description = "Precio negociado para este contrato")
        BigDecimal negotiatedPrice,

        @Schema(description = "Porcentaje de descuento adicional")
        BigDecimal discountPercentage,

        @Schema(description = "Precio efectivo después del descuento")
        BigDecimal effectivePrice,

        @Schema(description = "Inicio de vigencia")
        LocalDate validFrom,

        @Schema(description = "Fin de vigencia")
        LocalDate validTo,

        @Schema(description = "Override vigente a fecha de hoy")
        Boolean currentlyValid,

        @Schema(description = "Estado activo")
        Boolean active,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Usuario que creó el override")
        String createdBy
) {}
