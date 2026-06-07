package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DianResolutionResponseDto(

        @Schema(description = "ID de la resolución")
        Long id,

        @Schema(description = "Número de resolución DIAN")
        String resolutionNumber,

        @Schema(description = "Fecha de emisión de la resolución")
        LocalDate issueDate,

        @Schema(description = "Prefijo de facturación")
        String prefix,

        @Schema(description = "Tipo de documento habilitado")
        DocumentType documentType,

        @Schema(description = "Número inicial del rango autorizado")
        Long fromNumber,

        @Schema(description = "Número final del rango autorizado")
        Long toNumber,

        @Schema(description = "Inicio de vigencia")
        LocalDate validFrom,

        @Schema(description = "Fin de vigencia")
        LocalDate validTo,

        @Schema(description = "Consecutivo actual")
        Long currentConsecutive,

        @Schema(description = "Números disponibles restantes")
        Long remainingConsecutives,

        @Schema(description = "Resolución vigente y con consecutivos disponibles")
        Boolean valid,

        @Schema(description = "Resolución vencida por fecha")
        Boolean expired,

        @Schema(description = "Rango de consecutivos agotado")
        Boolean exhausted,

        @Schema(description = "Estado activo")
        Boolean active,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt
) {}
