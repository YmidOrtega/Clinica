package com.ClinicaDeYmid.admissions_service.module.mapper;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionUserHistoryResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.entity.AttentionUserHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttentionUserHistoryMapper {

    @Named("ToUserHistoryResponseDto")
    @Mapping(target = "user", expression = "java(null)")
    AttentionUserHistoryResponseDto toResponseDto(AttentionUserHistory entity);

    @Named("ToUserHistoryResponseDto")
    default List<AttentionUserHistoryResponseDto> toResponseDtoList(List<AttentionUserHistory> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(this::toResponseDto)
                .collect(java.util.stream.Collectors.toList());
    }

    default GetUserDto mapLongToGetUserDto(Long userId) {
        return null;
    }
}