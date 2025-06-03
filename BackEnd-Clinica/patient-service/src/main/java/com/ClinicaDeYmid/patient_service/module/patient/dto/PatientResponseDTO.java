package com.ClinicaDeYmid.patient_service.module.patient.dto;

import java.util.UUID;

public record PatientResponseDTO(
    UUID uuid,
    String name,
    String lastName,
    String identification,
    String mobile,
    String email
    //HealthPolicyDTO healthPolicyDTO
){

}
