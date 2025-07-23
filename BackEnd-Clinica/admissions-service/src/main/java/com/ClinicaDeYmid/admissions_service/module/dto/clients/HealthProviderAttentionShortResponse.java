package com.ClinicaDeYmid.admissions_service.module.dto.clients;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record HealthProviderAttentionShortResponse(
        Long attentionId,
        List<AuthorizationRequestDto> authorizations,
        String serviceName,
        Long invoiceNumber,
        List<String> diagnosticCodes,
        AttentionStatus status,
        LocalDateTime createdAt,
        Long invoicedByUserId

) {
}
