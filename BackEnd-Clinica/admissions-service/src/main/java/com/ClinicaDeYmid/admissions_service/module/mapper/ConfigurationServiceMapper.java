package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.ConfigurationServiceDto;
import com.ClinicaDeYmid.admissions_service.module.dto.ConfigurationServiceSummary;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        uses = {ServiceTypeMapper.class, LocationMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ConfigurationServiceMapper {

    ConfigurationServiceDto toDto(ConfigurationService entity);

    @Mapping(target = "serviceTypeName", source = "serviceType.name")
    @Mapping(target = "locationName", source = "location.name")
    ConfigurationServiceSummary toSummary(ConfigurationService entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ConfigurationService toEntity(ConfigurationServiceDto dto);
}