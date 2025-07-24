package com.ClinicaDeYmid.admissions_service.module.dto.suppliers;

import java.util.List;

public record GetDoctorDto(
        String identificationNumber,
        String name,
        String lastName,
        List<DoctorSpecialtyDto> specialties
) {
    public String fullName() {
        return name + " " + lastName;
    }
}
