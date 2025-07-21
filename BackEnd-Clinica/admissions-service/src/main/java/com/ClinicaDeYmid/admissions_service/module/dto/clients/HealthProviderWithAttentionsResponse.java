package com.ClinicaDeYmid.admissions_service.module.dto.clients;

import java.util.List;

public record HealthProviderWithAttentionsResponse<HealthProviderAttentionShortResponse>(
        String reasonSocial,
        List<HealthProviderAttentionShortResponse> attentions
) {}
