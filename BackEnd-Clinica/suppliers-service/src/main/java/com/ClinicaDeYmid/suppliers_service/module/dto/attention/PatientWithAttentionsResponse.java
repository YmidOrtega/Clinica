package com.ClinicaDeYmid.suppliers_service.module.dto.attention;

import java.util.List;

public record PatientWithAttentionsResponse(
        String patientName,
        List<PatientAttentionShortResponse> attentions
) {
}
