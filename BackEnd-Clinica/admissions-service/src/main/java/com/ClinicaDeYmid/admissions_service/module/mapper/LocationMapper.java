package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.LocationDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Location;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface LocationMapper {

    LocationDto toDto(Location entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Location toEntity(LocationDto dto);
}