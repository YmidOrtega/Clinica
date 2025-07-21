package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.*;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.CareTypeDto;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.LocationDto;
import com.ClinicaDeYmid.admissions_service.module.dto.catalog.ServiceTypeDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.HealthProviderAttentionShortResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.HealthProviderWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientAttentionShortResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.DoctorWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.entity.*;
import org.mapstruct.*;

import java.util.List;
import java.util.function.Function;

@Mapper(componentModel = "spring",
        uses = {AuthorizationMapper.class, AttentionUserHistoryMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttentionMapper {

    // Mapeo de Request a Entidad
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "invoiceNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dischargeDateTime", ignore = true)
    @Mapping(target = "userHistory", ignore = true)
    @Mapping(target = "configurationService", source = "configurationServiceId", qualifiedByName = "mapConfigurationServiceIdToEntity")
    @Mapping(target = "authorizations", ignore = true)
    Attention toEntity(AttentionRequestDto dto);

    // Actualización de Entidad desde Request DTO
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dischargeDateTime", ignore = true)
    @Mapping(target = "userHistory", ignore = true)
    @Mapping(target = "configurationService", source = "configurationServiceId", qualifiedByName = "mapConfigurationServiceIdToEntity")
    @Mapping(target = "authorizations", ignore = true)

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

    // Mapeos para PatientAttentionShortResponse
    @Mapping(target = "attentionId", source = "id")
    @Mapping(target = "configurationServiceName", source = "configurationService", qualifiedByName = "getConfigurationServiceName")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "invoiced", source = "invoiced")
    PatientAttentionShortResponse toPatientAttentionShortResponse(Attention attention);

    @Named("getConfigurationServiceName")
    default String getConfigurationServiceName(ConfigurationService configService) {
        if (configService == null) {
            return null;
        }

        StringBuilder serviceName = new StringBuilder();

        if (configService.getServiceType() != null) {
            serviceName.append(configService.getServiceType().getName());
        }


        if (configService.getServiceType() != null &&
                configService.getServiceType().getCareTypes() != null &&
                !configService.getServiceType().getCareTypes().isEmpty()) {
            String careTypeName = configService.getServiceType().getCareTypes().stream()
                    .findFirst()
                    .map(CareType::getName)
                    .orElse(null);
            if (careTypeName != null) {
                serviceName.append(" - ").append(careTypeName);
            }
        }

        if (configService.getLocation() != null) {
            serviceName.append(" (").append(configService.getLocation().getName()).append(")");
        }

        return serviceName.toString();
    }


    default PatientWithAttentionsResponse toPatientWithAttentionsResponse(String patientName, List<Attention> attentions) {
        if (attentions == null || attentions.isEmpty()) {
            return new PatientWithAttentionsResponse(patientName, List.of());
        }

        List<PatientAttentionShortResponse> attentionResponses = attentions.stream()
                .map(this::toPatientAttentionShortResponse)
                .toList();

        return new PatientWithAttentionsResponse(patientName, attentionResponses);
    }

    default DoctorWithAttentionsResponse toDoctorWithAttentionsResponse(
            String doctorName,
            List<PatientWithAttentionsResponse> patientAttentions) {
        return new DoctorWithAttentionsResponse(doctorName, patientAttentions);
    }

    default List<PatientWithAttentionsResponse> groupAttentionsByPatient(
            List<Attention> attentions,
            java.util.function.Function<Long, String> patientNameResolver) {

        if (attentions == null || attentions.isEmpty()) {
            return List.of();
        }

        return attentions.stream()
                .collect(java.util.stream.Collectors.groupingBy(Attention::getPatientId))
                .entrySet()
                .stream()
                .map(entry -> {
                    Long patientId = entry.getKey();
                    List<Attention> patientAttentions = entry.getValue();
                    String patientName = patientNameResolver.apply(patientId);
                    return toPatientWithAttentionsResponse(patientName, patientAttentions);
                })
                .toList();
    }

    @Mapping(target = "attentionId", source = "id")
    @Mapping(target = "authorizations", source = "authorizations")
    @Mapping(target = "configurationServiceName", source = "configurationService", qualifiedByName = "getConfigurationServiceName")
    @Mapping(target = "invoiceNumber", source = "invoiceNumber")
    @Mapping(target = "diagnosticCodes", source = "diagnosticCodes")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt")
    HealthProviderAttentionShortResponse toHealthProviderAttentionShortResponse(Attention attention);

    default HealthProviderWithAttentionsResponse toHealthProviderWithAttentionsResponse(
            String contractName,
            List<Attention> attentions) {
        if (attentions == null || attentions.isEmpty()) {
            return new HealthProviderWithAttentionsResponse(contractName, List.of());
        }

        List<HealthProviderAttentionShortResponse> attentionResponses = attentions.stream()
                .map(this::toHealthProviderAttentionShortResponse)
                .toList();

        return new HealthProviderWithAttentionsResponse(contractName, attentionResponses);
    }

    default List<HealthProviderWithAttentionsResponse> groupAttentionsByHealthProvider(
            List<Attention> attentions,
            Function<String, String> contractNameResolver) {

        if (attentions == null || attentions.isEmpty()) {
            return List.of();
        }

        java.util.Map<String, List<Attention>> groupedByProvider = new java.util.HashMap<>();

        for (Attention attention : attentions) {
            if (attention.getHealthProviderNit() != null && !attention.getHealthProviderNit().isEmpty()) {
                for (String nit : attention.getHealthProviderNit()) {
                    groupedByProvider.computeIfAbsent(nit, k -> new java.util.ArrayList<>()).add(attention);
                }
            }
        }

        return groupedByProvider.entrySet()
                .stream()
                .map(entry -> {
                    String nit = entry.getKey();
                    List<Attention> providerAttentions = entry.getValue();
                    String contractName = contractNameResolver.apply(nit);
                    return toHealthProviderWithAttentionsResponse(contractName, providerAttentions);
                })
                .toList();
    }

}