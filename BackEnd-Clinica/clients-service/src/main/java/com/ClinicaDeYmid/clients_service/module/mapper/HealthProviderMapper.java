package com.ClinicaDeYmid.clients_service.module.mapper;

import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderResponseDto;
import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;

import com.ClinicaDeYmid.clients_service.module.service.HealthProviderService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {HealthProviderService.class})
public interface HealthProviderMapper {

    // Mapeo de Entidad a Response DTO
    @Mapping(target = "contractStatus", expression = "java(healthProviderService.getContractStatus(healthProvider).getDisplayName())")
    HealthProviderResponseDto toResponseDto(HealthProvider healthProvider, HealthProviderService healthProviderService);

    // Mapeo de Create DTO a Entidad
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    HealthProvider toEntity(CreateHealthProviderDto createHealthProviderDto);




}