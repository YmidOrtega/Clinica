package com.ClinicaDeYmid.billing_service.module.config.mapper;

import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.BillingConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BillingConfigurationMapper {

    BillingConfigurationResponseDto toResponseDto(BillingConfiguration entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    BillingConfiguration toEntity(BillingConfigurationRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(BillingConfigurationRequestDto dto, @MappingTarget BillingConfiguration entity);
}
