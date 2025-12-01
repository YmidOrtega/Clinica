package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import java.util.List;

public record AttentionRequestDto(
        Long patientId,
        Long doctorId,
        Long configurationServiceId,
        String status,
        String cause,
        List<HealthProviderRequestDto> healthProviders,
        List<String> diagnosticCodes,
        String triageLevel,
        String entryMethod,
        CompanionDto companion,
        String observations,
        List<AuthorizationRequestDto> authorizations,
        Long userId
) {}
