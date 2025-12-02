package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthProviderRequestDto(
        @JsonProperty("nit")
        String nit,

        @JsonProperty("contract_id")
        Long contractId,

        @JsonProperty("authorization_number")
        String authorizationNumber,

        @JsonProperty("copayment")
        Double copayment,

        @JsonProperty("moderating_fee")
        Double moderatingFee
) {}
