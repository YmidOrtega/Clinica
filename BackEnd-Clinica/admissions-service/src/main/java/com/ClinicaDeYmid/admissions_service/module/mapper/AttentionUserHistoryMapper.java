package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionUserHistoryResponseDto;
import com.ClinicaDeYmid.admissions_service.module.entity.AttentionUserHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttentionUserHistoryMapper {

    @Named("ToUserHistoryResponseDto")
    @Mapping(target = "actionTimestamp", source = "actionTimestamp")
    AttentionUserHistoryResponseDto toResponseDto(AttentionUserHistory entity);

    @Named("ToUserHistoryResponseDto")
    List<AttentionUserHistoryResponseDto> toResponseDtoList(List<AttentionUserHistory> entities);
}