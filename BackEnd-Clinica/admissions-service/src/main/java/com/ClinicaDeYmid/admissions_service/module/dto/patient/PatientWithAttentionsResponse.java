package com.ClinicaDeYmid.admissions_service.module.dto.patient;

import com.ClinicaDeYmid.admissions_service.module.entity.Authorization;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PatientWithAttentionsResponse(
        String patientName,
        List<PatientAttentionShortResponse> attentions
) {}
