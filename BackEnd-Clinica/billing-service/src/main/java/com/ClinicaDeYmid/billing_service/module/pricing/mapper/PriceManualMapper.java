package com.ClinicaDeYmid.billing_service.module.pricing.mapper;

import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManual;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PriceManualMapper {

    @Mapping(target = "itemCount", expression = "java(entity.getItems().size())")
    PriceManualResponseDto toResponseDto(PriceManual entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    PriceManual toEntity(PriceManualRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(PriceManualRequestDto dto, @MappingTarget PriceManual entity);
}
