package com.ClinicaDeYmid.billing_service.infra.feignclient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DoctorFeignDto(
        Long id,
        String name,
        String lastName,
        String fullName,
        String identificationNumber,
        String licenseNumber,
        BigDecimal hourlyRate,
        List<SpecialtyFeignDto> specialties
) {
    public String resolvedFullName() {
        if (fullName != null && !fullName.isBlank()) return fullName;
        return (name != null ? name : "") + " " + (lastName != null ? lastName : "");
    }
}
