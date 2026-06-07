package com.ClinicaDeYmid.billing_service.module.sale.mapper;

import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderRequestDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderResponseDto;
import com.ClinicaDeYmid.billing_service.module.sale.dto.SaleOrderSummaryDto;
import com.ClinicaDeYmid.billing_service.module.sale.entity.SaleOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = SaleOrderItemMapper.class)
public interface SaleOrderMapper {

    @Mapping(target = "items", source = "items")
    SaleOrderResponseDto toResponseDto(SaleOrder entity);

    @Mapping(target = "itemCount", expression = "java(entity.getItems().size())")
    SaleOrderSummaryDto toSummaryDto(SaleOrder entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "copaymentAmount", ignore = true)
    @Mapping(target = "netAmount", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    SaleOrder toEntity(SaleOrderRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "copaymentAmount", ignore = true)
    @Mapping(target = "netAmount", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(SaleOrderRequestDto dto, @MappingTarget SaleOrder entity);
}
