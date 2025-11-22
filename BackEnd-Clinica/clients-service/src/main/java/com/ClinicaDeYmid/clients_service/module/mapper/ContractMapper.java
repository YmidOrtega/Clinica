package com.ClinicaDeYmid.clients_service.module.mapper;

import com.ClinicaDeYmid.clients_service.module.dto.ContractDto;
import com.ClinicaDeYmid.clients_service.module.dto.UpdateContractDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ContractMapper {

    ContractDto toContractDto(Contract contract);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "healthProvider", ignore = true)
    @Mapping(target = "coveredServices", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletionReason", ignore = true)
    Contract toEntity(UpdateContractDto updateContractDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "healthProvider", ignore = true)
    @Mapping(target = "coveredServices", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletionReason", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateContractFromDto(UpdateContractDto updateContractDto, @MappingTarget Contract contract);
}