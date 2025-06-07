package com.ClinicaDeYmid.patient_service.module.patient.dto;

import com.ClinicaDeYmid.patient_service.module.patient.entity.enums.IdentificationType;

public record PatientsListDto(
        String identificationNumber,
        String name,
        String lastName
        ) {
}
