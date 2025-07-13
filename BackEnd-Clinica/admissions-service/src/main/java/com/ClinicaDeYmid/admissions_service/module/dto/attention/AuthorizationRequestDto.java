package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import jakarta.validation.constraints.*;
import java.util.List;

public record AuthorizationRequestDto(
        @Schema(description = "ID de la autorización", example = "88")
        Long id,

        @Schema(description = "Número de la autorización", example = "AUTH-2024-999", required = true)
        @NotBlank @Size(max = 255)
        String authorizationNumber,

        @Schema(description = "Tipo de autorización", example = "SERVICIO", required = true, implementation = TypeOfAuthorization.class)
        @NotNull
        TypeOfAuthorization typeOfAuthorization,

        @Schema(description = "Quién autorizó", example = "Coordinador de Servicios", required = true)
        @NotBlank @Size(max = 255)
        String authorizationBy,

        @Schema(description = "Valor de copago", example = "20000", required = true)
        @NotNull
        Double copaymentValue,

        @Schema(description = "IDs de portafolios autorizados", example = "[10,11]", required = true)
        @NotNull
        List<Long> authorizedPortfolioIds
) {}