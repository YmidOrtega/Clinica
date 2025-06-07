package com.ClinicaDeYmid.patient_service.module.patient.dto;

import java.time.LocalDateTime;

public record PatientResponseDto(
        String uuid,
        String name,
        String lastName,
        String identificationNumber,
        String email,
        LocalDateTime createdAt //,
        //HealthPolicyDto healthPolicyId
){

}
