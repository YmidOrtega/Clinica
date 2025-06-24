package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.*;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.Companion;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {ConfigurationServiceMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface AttentionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "dischargeDateTime", ignore = true)
    @Mapping(target = "invoiced", constant = "false")
    @Mapping(target = "configurationService", source = "configurationServiceId")
    @Mapping(target = "companion", source = "companion")
    Attention toEntity(CreateAttentionRequestDto dto);


    @Mapping(target = "patientId", source = "patientId")
    @Mapping(target = "doctorId", source = "doctorId")
    @Mapping(target = "configurationService", source = "configurationService")
    AttentionSummary toSummary(Attention entity);

    List<AttentionSummary> toSummaryList(List<Attention> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "attention", ignore = true)
    Companion companionDtoToEntity(CompanionDto dto);

    CompanionDto companionEntityToDto(Companion entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromDto(CreateAttentionRequestDto dto, @MappingTarget Attention entity);

    default ConfigurationService map(Long configServiceId) {
        if (configServiceId == null) {
            return null;
        }
        ConfigurationService configService = new ConfigurationService();
        configService.setId(configServiceId);
        return configService;
    }
}