package com.ClinicaDeYmid.admissions_service.module.dto;

import java.util.List;

public record PagedAttentionResponse(
        List<AttentionSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {}
