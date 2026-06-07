package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.DianEnvironment;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxRegime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record BillingConfigurationResponseDto(

        @Schema(description = "ID de la configuración")
        Long id,

        @Schema(description = "NIT de la clínica emisora")
        String clinicNit,

        @Schema(description = "Razón social de la clínica")
        String socialReason,

        @Schema(description = "Régimen tributario")
        TaxRegime taxRegime,

        @Schema(description = "Ambiente DIAN activo")
        DianEnvironment dianEnvironment,

        @Schema(description = "ID del software de facturación")
        String softwareId,

        @Schema(description = "Ciudad de la clínica")
        String city,

        @Schema(description = "Dirección de la clínica")
        String address,

        @Schema(description = "Teléfono de contacto")
        String phone,

        @Schema(description = "Correo electrónico de contacto")
        String email,

        @Schema(description = "Estado activo de la configuración")
        Boolean active,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Usuario que creó la configuración")
        String createdBy
) {}
