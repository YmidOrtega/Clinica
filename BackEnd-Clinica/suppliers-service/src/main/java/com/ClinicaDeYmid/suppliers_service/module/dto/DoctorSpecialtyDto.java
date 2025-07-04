package com.ClinicaDeYmid.suppliers_service.module.dto;

import java.util.List;

public record DoctorSpecialtyDto(
        String name,
        int codeSpeciality,
        List<SubSpecialtyDetailsDto> subSpecialtiesDetails
) {}
