package com.ClinicaDeYmid.billing_service.module.config.mapper;

import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.TaxConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TaxConfigurationMapper {

    TaxConfigurationResponseDto toResponseDto(TaxConfiguration entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "appliesToServices", defaultValue = "true")
    @Mapping(target = "appliesToMedications", defaultValue = "false")
    TaxConfiguration toEntity(TaxConfigurationRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TaxConfigurationRequestDto dto, @MappingTarget TaxConfiguration entity);
}
