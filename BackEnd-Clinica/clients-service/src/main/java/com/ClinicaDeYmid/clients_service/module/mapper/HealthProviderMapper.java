package com.ClinicaDeYmid.clients_service.module.mapper;

import com.ClinicaDeYmid.clients_service.module.dto.*;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HealthProviderMapper {

    // Mapeo de Entidad a Response DTO
    @Mapping(target = "contractStatus",
            expression = "java(healthProvider.getContracts() != null && !healthProvider.getContracts().isEmpty() ? healthProvider.getContracts().get(0).getStatus() : null)")
    HealthProviderResponseDto toResponseDto(HealthProvider healthProvider);

    // Mapeo de Create DTO a Entidad
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contracts", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletionReason", ignore = true)
    HealthProvider toEntity(CreateHealthProviderDto createHealthProviderDto);

    HealthProviderListDto toHealthProviderListDto(HealthProvider healthProvider);

    @Mapping(source = "contract.id", target = "id")
    @Mapping(source = "contract.contractName", target = "contractName")
    @Mapping(source = "contract.contractNumber", target = "contractNumber")
    @Mapping(source = "contract.agreedTariff", target = "agreedTariff")
    @Mapping(source = "contract.startDate", target = "startDate")
    @Mapping(source = "contract.endDate", target = "endDate")
    @Mapping(source = "contract.status", target = "status")
    @Mapping(source = "contract.active", target = "active")
    ContractDto toContractDto(Contract contract);

    default GetHealthProviderDto toGetHealthProviderDto(HealthProvider provider, Contract contract) {
        return new GetHealthProviderDto(
                provider.getNit().getFormattedNit(),
                provider.getSocialReason(),
                provider.getTypeProvider().name(),
                toContractDto(contract)
        );
    }
}