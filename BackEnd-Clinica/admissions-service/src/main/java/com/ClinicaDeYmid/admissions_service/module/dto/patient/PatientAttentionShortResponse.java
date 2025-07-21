package com.ClinicaDeYmid.admissions_service.module.dto.patient;

import java.time.LocalDateTime;

public record PatientAttentionShortResponse(
        Long attentionId,
        String configurationServiceName,
        LocalDateTime createdAt,
        boolean invoiced
) {}
