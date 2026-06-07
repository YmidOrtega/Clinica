package com.ClinicaDeYmid.billing_service.module.sale.mapper;

import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemRequestDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderItemResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SaleOrderItemMapper {

    @Mapping(target = "saleOrderId", source = "saleOrder.id")
    SaleOrderItemResponseDto toResponseDto(SaleOrderItem entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "saleOrder", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "authorized", defaultValue = "false")
    SaleOrderItem toEntity(SaleOrderItemRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "saleOrder", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    void updateEntity(SaleOrderItemRequestDto dto, @MappingTarget SaleOrderItem entity);
}
