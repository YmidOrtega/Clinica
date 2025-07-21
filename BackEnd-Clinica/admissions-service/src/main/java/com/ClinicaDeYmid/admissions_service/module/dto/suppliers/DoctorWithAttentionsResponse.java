package com.ClinicaDeYmid.admissions_service.module.dto.suppliers;

import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;

import java.util.List;

public record DoctorWithAttentionsResponse(
        String doctorName,
        List<PatientWithAttentionsResponse> attentions
) {}
