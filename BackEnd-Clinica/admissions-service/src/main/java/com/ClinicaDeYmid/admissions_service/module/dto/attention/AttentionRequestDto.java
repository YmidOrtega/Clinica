package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;

public record AttentionRequestDto(
        Long id,

        @NotNull(message = "El ID del paciente no puede ser nulo")
        Long patientId,

        @NotNull(message = "El ID del doctor no puede ser nulo")
        Long doctorId,

        @NotNull(message = "El ID del servicio de configuración no puede ser nulo")
        Long configurationServiceId,

        @NotNull(message = "El estado de la atención no puede ser nulo")
        AttentionStatus status,

        @NotNull(message = "La causa de la atención no puede ser nulo")
        Cause cause,

        @Size(min = 1, message = "Debe haber al menos un proveedor de salud")
        List<@NotBlank String> healthProviderNit,

        @Size(max = 50) String entryMethod,
        @Size(max = 100) String referringEntity,
        Boolean isReferral,
        @Size(max = 20) String mainDiagnosisCode,
        List<@Size(max = 20) String> secondaryDiagnosisCodes,
        TriageLevel triageLevel,

        @Valid
        CompanionDto companion,

        @Size(max = 1000) String observations,
        @Size(max = 1000) String billingObservations,

        List<@Valid AuthorizationRequestDto> authorizations,

        @NotNull(message = "El ID del usuario que realiza la acción no puede ser nulo")
        Long userId
) {}