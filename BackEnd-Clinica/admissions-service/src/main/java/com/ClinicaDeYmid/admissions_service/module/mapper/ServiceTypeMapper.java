package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.ServiceTypeDto;
import com.ClinicaDeYmid.admissions_service.module.entity.ServiceType;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        uses = {CareTypeMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ServiceTypeMapper {

    ServiceTypeDto toDto(ServiceType entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ServiceType toEntity(ServiceTypeDto dto);
}