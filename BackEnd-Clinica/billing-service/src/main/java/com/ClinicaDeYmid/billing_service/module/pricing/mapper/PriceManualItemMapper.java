package com.ClinicaDeYmid.billing_service.module.pricing.mapper;

import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualItemRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualItemResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManualItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PriceManualItemMapper {

    @Mapping(target = "priceManualId", source = "priceManual.id")
    PriceManualItemResponseDto toResponseDto(PriceManualItem entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "priceManual", ignore = true)
    @Mapping(target = "active", constant = "true")
    PriceManualItem toEntity(PriceManualItemRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "priceManual", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(PriceManualItemRequestDto dto, @MappingTarget PriceManualItem entity);
}
