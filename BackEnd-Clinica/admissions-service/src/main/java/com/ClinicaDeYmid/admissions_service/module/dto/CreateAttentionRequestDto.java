package com.ClinicaDeYmid.admissions_service.module.dto;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.validation.TriageLevelRequiredForEmergency;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

@TriageLevelRequiredForEmergency

public record CreateAttentionRequestDto(
        @NotNull(message = "El ID del paciente es obligatorio")
        @Positive(message = "El ID del paciente debe ser positivo")
        Long patientId,

        @NotNull(message = "El ID del doctor es obligatorio")
        @Positive(message = "El ID del doctor debe ser positivo")
        Long doctorId,

        @NotEmpty(message = "Debe especificar al menos un proveedor de salud")
        @Size(max = 10, message = "Máximo 10 proveedores de salud permitidos")
        List<@Positive(message = "Los IDs de proveedores deben ser positivos") Long> healthProviderIds,

        @NotNull ConfigurationServiceDto configurationServiceId,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime admissionDateTime,

        @NotNull(message = "El estado es obligatorio")
        AttentionStatus status,

        @Size(max = 50, message = "El método de entrada no puede exceder 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9\\s._-]*$", message = "El método de entrada contiene caracteres no válidos")
        String entryMethod,

        @Size(max = 100, message = "La entidad referente no puede exceder 100 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9\\s._-]*$", message = "La entidad referente contiene caracteres no válidos")
        String referringEntity,

        Boolean isReferral,

        @Size(max = 20, message = "El código de diagnóstico principal no puede exceder 20 caracteres")
        @Pattern(regexp = "^[A-Z0-9.-]*$", message = "El código de diagnóstico debe seguir el formato estándar")
        String mainDiagnosisCode,

        @Size(max = 10, message = "Máximo 10 diagnósticos secundarios permitidos")
        List<@Size(max = 20, message = "Cada código de diagnóstico secundario no puede exceder 20 caracteres")
        @Pattern(regexp = "^[A-Z0-9.-]*$", message = "Los códigos de diagnóstico deben seguir el formato estándar") String> secondaryDiagnosisCodes,

        TriageLevel triageLevel,

        @NotNull(message = "La causa es obligatoria")
        Cause cause,

        @Valid
        CompanionDto companion,

        @Size(max = 1000, message = "Las observaciones no pueden exceder 1000 caracteres")
        String observations,

        @Size(max = 1000, message = "Las observaciones de facturación no pueden exceder 1000 caracteres")
        String billingObservations
) {
}
