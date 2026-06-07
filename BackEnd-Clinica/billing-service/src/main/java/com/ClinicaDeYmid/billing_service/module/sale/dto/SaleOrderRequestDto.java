package com.ClinicaDeYmid.billing_service.module.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SaleOrderRequestDto(

        @Schema(description = "ID de la atención en admissions-service", example = "101")
        @NotNull(message = "El ID de la atención es obligatorio.")
        Long attentionId,

        @Schema(description = "Número de identificación del paciente", example = "1020304050")
        @NotBlank(message = "El ID del paciente es obligatorio.")
        @Size(max = 20, message = "El ID del paciente no puede exceder 20 caracteres.")
        String patientId,

        @Schema(description = "NIT del prestador de salud", example = "900123456-7")
        @NotBlank(message = "El NIT del prestador es obligatorio.")
        @Pattern(regexp = "^[0-9]{6,12}(-[0-9])?$",
                message = "El NIT debe tener formato válido.")
        String healthProviderNit,

        @Schema(description = "ID del contrato en clients-service", example = "15")
        Long contractId,

        @Schema(description = "ID del médico en suppliers-service", example = "8")
        Long doctorId,

        @Schema(description = "Observaciones de la venta", example = "Atención de urgencias")
        @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres.")
        String notes
) {}
