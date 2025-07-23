package com.ClinicaDeYmid.suppliers_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "AttentionGetDto",
        description = "DTO que representa la información de una atención médica"
)
public record AttentionGetDto(
        String doctorName,
        List<PatientWithAttentionsResponse> attentions
) { }
