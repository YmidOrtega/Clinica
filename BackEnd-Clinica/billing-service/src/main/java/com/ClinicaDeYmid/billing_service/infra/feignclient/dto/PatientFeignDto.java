package com.ClinicaDeYmid.billing_service.infra.feignclient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientFeignDto(
        String identificationNumber,
        String identificationType,
        String name,
        String lastName,
        String gender,
        String healthPolicyNumber,
        String address,
        String phone,
        String mobile,
        String email
) {
    public String fullName() {
        return (name != null ? name : "") + " " + (lastName != null ? lastName : "");
    }
}
