package dto;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import enums.ContractStatus;
import enums.TypeProvider;


import java.util.List;

public record HealthProviderResponseDto(
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        List<Contract> contracts,
        ContractStatus contractStatus

) {
}
