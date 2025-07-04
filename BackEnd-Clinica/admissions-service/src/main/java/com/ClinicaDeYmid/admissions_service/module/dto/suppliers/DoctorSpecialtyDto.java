package com.ClinicaDeYmid.admissions_service.module.dto.suppliers;

import java.util.List;

public record DoctorSpecialtyDto(
        String name,
        int codeSpeciality,
        List<SubSpecialtyDetailsDto> subSpecialtiesDetails
) {}
