package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.CareTypeDto;
import com.ClinicaDeYmid.admissions_service.module.entity.CareType;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CareTypeMapper {

    CareTypeDto toDto(CareType entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CareType toEntity(CareTypeDto dto);
}