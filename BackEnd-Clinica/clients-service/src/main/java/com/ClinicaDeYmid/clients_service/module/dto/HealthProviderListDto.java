package com.ClinicaDeYmid.clients_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;

public record HealthProviderListDto(
        @Schema(description = "Provider ID", example = "15")
        Long id,

        @Schema(description = "Social reason (legal name) of the provider", example = "Salud Total S.A.")
        String socialReason,

        @Schema(description = "NIT (tax identification number)", example = "900123456-7")
        Nit nit,

        @Schema(description = "Type of provider", example = "EPS", implementation = TypeProvider.class)
        TypeProvider typeProvider,

        @Schema(description = "Provider address", example = "Calle 123 #45-67")
        String address,

        @Schema(description = "Provider phone number", example = "+5712345678")
        String phone,

        @Schema(description = "Whether the provider is active", example = "true")
        Boolean active
) {
}
