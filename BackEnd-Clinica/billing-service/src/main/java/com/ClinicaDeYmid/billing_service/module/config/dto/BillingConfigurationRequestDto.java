package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.DianEnvironment;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxRegime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record BillingConfigurationRequestDto(

        @Schema(description = "NIT de la clínica emisora", example = "900123456-7")
        @NotBlank(message = "El NIT es obligatorio.")
        @Pattern(regexp = "^[0-9]{6,12}(-[0-9])?$",
                message = "El NIT debe tener formato válido (6 a 12 dígitos, opcionalmente seguido de guión y dígito verificador).")
        String clinicNit,

        @Schema(description = "Razón social de la clínica", example = "Clínica De Ymid S.A.S.")
        @NotBlank(message = "La razón social es obligatoria.")
        @Size(min = 2, max = 300, message = "La razón social debe tener entre 2 y 300 caracteres.")
        String socialReason,

        @Schema(description = "Régimen tributario", example = "RESPONSABLE_DE_IVA")
        @NotNull(message = "El régimen tributario es obligatorio.")
        TaxRegime taxRegime,

        @Schema(description = "Ambiente DIAN para facturación electrónica", example = "HABILITACION")
        @NotNull(message = "El ambiente DIAN es obligatorio.")
        DianEnvironment dianEnvironment,

        @Schema(description = "ID del software de facturación habilitado ante la DIAN", example = "abc123")
        @Size(max = 100, message = "El ID del software no puede exceder 100 caracteres.")
        String softwareId,

        @Schema(description = "PIN del software de facturación", example = "12345")
        @Size(max = 100, message = "El PIN del software no puede exceder 100 caracteres.")
        String softwarePin,

        @Schema(description = "Ciudad de la clínica", example = "Bogotá")
        @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres.")
        String city,

        @Schema(description = "Dirección de la clínica", example = "Calle 123 #45-67")
        @Size(max = 300, message = "La dirección no puede exceder 300 caracteres.")
        String address,

        @Schema(description = "Teléfono de contacto", example = "+5712345678")
        @Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$",
                message = "El teléfono debe tener un formato válido (7 a 20 caracteres).")
        String phone,

        @Schema(description = "Correo electrónico de contacto", example = "facturacion@clinicadeymid.com")
        @Email(message = "El correo electrónico debe tener un formato válido.")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres.")
        String email
) {}
