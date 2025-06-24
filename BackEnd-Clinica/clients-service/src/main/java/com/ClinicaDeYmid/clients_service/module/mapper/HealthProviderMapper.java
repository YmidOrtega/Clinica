package com.ClinicaDeYmid.clients_service.module.mapper;


import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;

import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HealthProviderMapper {

    // Mapeo de Entidad a Response DTO
    @Mapping(target = "contractStatus",
            expression = "java(healthProvider.getContracts() != null && !healthProvider.getContracts().isEmpty() ? healthProvider.getContracts().get(0).getStatus() : null)")
    HealthProviderResponseDto toResponseDto(HealthProvider healthProvider);

    // Mapeo de Create DTO a Entidad
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contracts", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    HealthProvider toEntity(CreateHealthProviderDto createHealthProviderDto);

    HealthProviderListDto toHealthProviderListDto(HealthProvider healthProvider);

    /*
    // Mapeo de Update DTO a Entidad (para PUT/PATCH)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contracts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateHealthProviderDto updateHealthProviderDto, @MappingTarget HealthProvider healthProvider);
    */
}