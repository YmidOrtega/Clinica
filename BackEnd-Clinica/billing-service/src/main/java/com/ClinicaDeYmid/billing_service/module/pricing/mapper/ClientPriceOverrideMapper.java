package com.ClinicaDeYmid.billing_service.module.pricing.mapper;

import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.ClientPriceOverride;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring")
public interface ClientPriceOverrideMapper {

    @Mapping(target = "effectivePrice",
            expression = "java(calculateEffectivePrice(entity))")
    @Mapping(target = "currentlyValid",
            expression = "java(entity.isCurrentlyValid())")
    ClientPriceOverrideResponseDto toResponseDto(ClientPriceOverride entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    ClientPriceOverride toEntity(ClientPriceOverrideRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(ClientPriceOverrideRequestDto dto, @MappingTarget ClientPriceOverride entity);

    default BigDecimal calculateEffectivePrice(ClientPriceOverride entity) {
        if (entity.getDiscountPercentage() == null
                || entity.getDiscountPercentage().compareTo(BigDecimal.ZERO) == 0) {
            return entity.getNegotiatedPrice();
        }
        BigDecimal factor = BigDecimal.ONE
                .subtract(entity.getDiscountPercentage().divide(BigDecimal.valueOf(100)));
        return entity.getNegotiatedPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
