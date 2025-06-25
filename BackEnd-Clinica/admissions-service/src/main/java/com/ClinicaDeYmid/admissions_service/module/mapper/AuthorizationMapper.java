package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationResponseDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Authorization;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthorizationMapper {

    @Mapping(target = "attention", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Authorization toEntity(AuthorizationRequestDto dto);

    @Named("ToAuthorizationResponseDto")
    AuthorizationResponseDto toResponseDto(Authorization entity);

    @Named("ToAuthorizationResponseDto")
    List<AuthorizationResponseDto> toResponseDtoList(List<Authorization> entities);
}