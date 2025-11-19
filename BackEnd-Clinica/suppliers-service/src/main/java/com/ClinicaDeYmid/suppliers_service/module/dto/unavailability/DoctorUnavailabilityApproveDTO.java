package com.ClinicaDeYmid.suppliers_service.module.dto.unavailability;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DoctorUnavailabilityApproveDTO(
        @Schema(description = "ID de la ausencia a aprobar", example = "1", required = true)
        @NotNull(message = "El ID de la ausencia es obligatorio")
        Long unavailabilityId,

        @Schema(description = "Usuario que aprueba", example = "admin", required = true)
        @NotNull(message = "El usuario que aprueba es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre de usuario debe tener entre 3 y 100 caracteres")
        String approvedBy
) {}
