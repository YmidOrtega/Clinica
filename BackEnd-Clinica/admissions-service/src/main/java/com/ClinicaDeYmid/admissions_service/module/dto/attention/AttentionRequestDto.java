package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;

public record AttentionRequestDto(
        @Schema(description = "ID de la atención (solo para actualizar)", example = "150")
        Long id,

        @Schema(description = "ID del paciente", example = "123", required = true)
        @NotNull(message = "El ID del paciente no puede ser nulo")
        Long patientId,

        @Schema(description = "ID del doctor", example = "85", required = true)
        @NotNull(message = "El ID del doctor no puede ser nulo")
        Long doctorId,

        @Schema(description = "ID del servicio de configuración", example = "20", required = true)
        @NotNull(message = "El ID del servicio de configuración no puede ser nulo")
        Long configurationServiceId,

        @Schema(description = "Estado de la atención", example = "IN_PROGRESS", required = true, implementation = AttentionStatus.class)
        @NotNull(message = "El estado de la atención no puede ser nulo")
        AttentionStatus status,

        @Schema(description = "Causa de la atención", example = "ACCIDENT", required = true, implementation = Cause.class)
        @NotNull(message = "La causa de la atención no puede ser nulo")
        Cause cause,

        @Schema(description = "Lista de NITs de proveedores de salud", example = "[\"900123456-1\"]", required = true)
        @Size(min = 1, message = "Debe haber al menos un proveedor de salud")
        List<@NotBlank String> healthProviderNit,

        @Schema(description = "Lista de códigos de diagnóstico (CIE10)", example = "[\"A01\", \"B02\"]")
        List<@Size(max = 20) String> diagnosticCodes,

        @Schema(description = "Nivel de triaje", example = "II", implementation = TriageLevel.class)
        TriageLevel triageLevel,

        @Schema(description = "Método de entrada (ejemplo: Urgencias)", example = "Urgencias")
        @Size(max = 50, message = "El método de entrada no puede exceder 50 caracteres")
        String entryMethod,

        @Schema(description = "Información del acompañante")
        @Valid
        CompanionDto companion,

        @Schema(description = "Observaciones adicionales", example = "Paciente con antecedentes de diabetes.")
        @Size(max = 1000)
        String observations,

        @Schema(description = "Lista de autorizaciones asociadas")
        List<@Valid AuthorizationRequestDto> authorizations,

        @Schema(description = "ID del usuario que realiza la acción", example = "42", required = true)
        @NotNull(message = "El ID del usuario que realiza la acción no puede ser nulo")
        Long userId
) {}
