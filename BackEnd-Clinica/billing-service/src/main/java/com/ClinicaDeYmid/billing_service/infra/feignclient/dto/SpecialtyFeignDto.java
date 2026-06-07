package com.ClinicaDeYmid.billing_service.infra.feignclient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpecialtyFeignDto(
        Long id,
        String name,
        Integer codeSpeciality
) {}
