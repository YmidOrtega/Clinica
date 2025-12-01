package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

public record AttentionResponseDto(
        Long id,
        Long patientId,
        Long doctorId,
        String status,
        String attentionNumber
) {}
