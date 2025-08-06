package com.ClinicaDeYmid.admissions_service.module.dto.patient;

import java.util.List;

public record PatientWithAttentionsResponse(
        String patientName,
        List<PatientAttentionShortResponse> attentions
) {}
