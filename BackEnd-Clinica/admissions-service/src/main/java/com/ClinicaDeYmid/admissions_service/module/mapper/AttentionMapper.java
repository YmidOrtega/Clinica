package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.*;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.CareTypeDto;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.LocationDto;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.ServiceTypeDto;
import com.ClinicaDeYmid.admissions_service.module.entity.*;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        uses = {AuthorizationMapper.class, AttentionUserHistoryMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttentionMapper {

    // Mapeo de Request a Entidad
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "invoiceNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "admissionDateTime", ignore = true)
    @Mapping(target = "dischargeDateTime", ignore = true)
    @Mapping(target = "userHistory", ignore = true)
    @Mapping(target = "configurationService", source = "configurationServiceId", qualifiedByName = "mapConfigurationServiceIdToEntity")
    @Mapping(target = "authorizations", ignore = true)
    @Mapping(target = "healthProviderNit", source = "healthProviderNit")
    Attention toEntity(AttentionRequestDto dto);

    // Actualización de Entidad desde Request DTO
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "admissionDateTime", ignore = true)
    @Mapping(target = "dischargeDateTime", ignore = true)
    @Mapping(target = "userHistory", ignore = true)
    @Mapping(target = "configurationService", source = "configurationServiceId", qualifiedByName = "mapConfigurationServiceIdToEntity")
    @Mapping(target = "authorizations", ignore = true)
    @Mapping(target = "healthProviderNit", source = "healthProviderNit")
    void updateEntityFromDto(AttentionRequestDto dto, @MappingTarget Attention entity);

    // Mapeo de Entidad a Response DTO
    @Mapping(target = "patientDetails", ignore = true)
    @Mapping(target = "doctorDetails", ignore = true)
    @Mapping(target = "healthProviderDetails", ignore = true)
    @Mapping(target = "userHistory", source = "userHistory", qualifiedByName = "ToUserHistoryResponseDto")
    @Mapping(target = "authorizations", source = "authorizations", qualifiedByName = "ToAuthorizationResponseDto")
    @Mapping(target = "configurationService", source = "configurationService", qualifiedByName = "ToConfigurationServiceResponseDto")
    AttentionResponseDto toResponseDto(Attention entity);

    List<AttentionResponseDto> toResponseDtoList(List<Attention> entities);

    @Named("mapConfigurationServiceIdToEntity")
    default ConfigurationService mapConfigurationServiceIdToEntity(Long configurationServiceId) {
        if (configurationServiceId == null) {
            return null;
        }
        ConfigurationService configService = new ConfigurationService();
        configService.setId(configurationServiceId);
        return configService;
    }


    // Metodo para mapear ConfigurationService a ConfigurationServiceResponseDto
    @Named("ToConfigurationServiceResponseDto")
    default ConfigurationServiceResponseDto mapConfigurationServiceToResponseDto(ConfigurationService configService) {
        if (configService == null) {
            return null;
        }

        String careTypeName = null;
        if (configService.getServiceType() != null && configService.getServiceType().getCareTypes() != null) {
            careTypeName = configService.getServiceType().getCareTypes().stream()
                    .findFirst()
                    .map(CareType::getName)
                    .orElse(null);
        }

        String serviceTypeName = configService.getServiceType() != null ? configService.getServiceType().getName() : null;
        String locationName = configService.getLocation() != null ? configService.getLocation().getName() : null;

        return new ConfigurationServiceResponseDto(
                configService.getId(),
                serviceTypeName,
                careTypeName,
                locationName,
                configService.isActive()
        );
    }

    // Mapeo para Companion
    CompanionDto toCompanionDto(Companion companion);
    Companion toCompanionEntity(CompanionDto companionDto);

    // Mapeo para CareType
    @Named("toCareTypeDto")
    default CareTypeDto toCareTypeDto(CareType careType) {
        if (careType == null) {
            return null;
        }
        return new CareTypeDto(careType.getId(), careType.getName());
    }

    // Mapeo para Location
    @Named("toLocationDto")
    default LocationDto toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationDto(location.getId(), location.getName());
    }

    // Mapeo para ServiceType
    @Named("toServiceTypeDto")
    default ServiceTypeDto toServiceTypeDto(ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }
        return new ServiceTypeDto(
                serviceType.getId(),
                serviceType.getName(),
                toCareTypeDto(serviceType.getCareTypes().iterator().next()),
                serviceType.isActive()
        );
    }
}