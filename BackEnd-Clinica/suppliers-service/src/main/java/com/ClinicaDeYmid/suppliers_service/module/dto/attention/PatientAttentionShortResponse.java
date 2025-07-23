package com.ClinicaDeYmid.suppliers_service.module.dto.attention;

import java.time.LocalDateTime;

public record PatientAttentionShortResponse(
        Long attentionId,
        String configurationServiceName,
        LocalDateTime createdAt,
        boolean invoiced,
        Long invoicedByUserId
) {
}
