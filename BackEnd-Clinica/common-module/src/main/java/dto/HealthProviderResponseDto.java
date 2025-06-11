package dto;


import domain.Nit;
import enums.ContractStatus;
import enums.TypeProvider;


import java.util.List;


public record HealthProviderResponseDto(
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        List<ContractDto> contracts,
        ContractStatus contractStatus

) {}
