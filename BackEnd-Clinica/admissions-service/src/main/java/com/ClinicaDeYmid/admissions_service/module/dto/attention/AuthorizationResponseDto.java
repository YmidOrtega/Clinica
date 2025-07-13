package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import java.time.LocalDateTime;
import java.util.List;

public record AuthorizationResponseDto(
        @Schema(description = "ID de la autorización", example = "88")
        Long id,

        @Schema(description = "Número de la autorización", example = "AUTH-2024-999")
        String authorizationNumber,

        @Schema(description = "Tipo de autorización", example = "SERVICIO", implementation = TypeOfAuthorization.class)
        TypeOfAuthorization typeOfAuthorization,

        @Schema(description = "Quién autorizó", example = "Coordinador de Servicios")
        String authorizationBy,

        @Schema(description = "Valor de copago", example = "20000")
        Double copaymentValue,

        @Schema(description = "IDs de portafolios autorizados", example = "[10,11]")
        List<Long> authorizedPortfolioIds,

        @Schema(description = "Fecha de creación", example = "2024-07-12T17:00:00")
        LocalDateTime createdAt
) {}
