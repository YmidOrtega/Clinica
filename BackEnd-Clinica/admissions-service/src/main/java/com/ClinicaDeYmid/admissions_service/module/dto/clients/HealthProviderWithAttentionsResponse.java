package com.ClinicaDeYmid.admissions_service.module.dto.clients;

import java.util.List;

public record HealthProviderWithAttentionsResponse<T>(
        String reasonSocial,
        List<T> attentions
) {}
