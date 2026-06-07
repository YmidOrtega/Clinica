package com.ClinicaDeYmid.billing_service.module.config.mapper;

import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.DianResolution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DianResolutionMapper {

    @Mapping(target = "remainingConsecutives",
            expression = "java(entity.getToNumber() - entity.getCurrentConsecutive() + 1)")
    @Mapping(target = "valid", expression = "java(entity.isValid())")
    @Mapping(target = "expired", expression = "java(entity.isExpired())")
    @Mapping(target = "exhausted", expression = "java(entity.isExhausted())")
    DianResolutionResponseDto toResponseDto(DianResolution entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "currentConsecutive", expression = "java(dto.fromNumber())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DianResolution toEntity(DianResolutionRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "currentConsecutive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(DianResolutionRequestDto dto, @MappingTarget DianResolution entity);
}
