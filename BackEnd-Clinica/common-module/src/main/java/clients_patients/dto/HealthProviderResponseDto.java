package clients_patients.dto;


import clients_patients.domain.Nit;
import clients_patients.enums.ContractStatus;
import clients_patients.enums.TypeProvider;


import java.util.List;


public record HealthProviderResponseDto(
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        List<ContractDto> contracts,
        ContractStatus contractStatus

) {}
