package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AttentionExtractionResult(

        @JsonProperty("patient_id")
        String patientId,

        @JsonProperty("doctor_id")
        Long doctorId,

        @JsonProperty("configuration_service_id")
        Long configurationServiceId,

        @JsonProperty("cause")
        String cause,

        @JsonProperty("triage_level")
        String triageLevel,

        @JsonProperty("entry_method")
        String entryMethod,

        @JsonProperty("health_providers")
        List<HealthProviderData> healthProviders,

        @JsonProperty("observations")
        String observations

) {
    public record HealthProviderData(
            @JsonProperty("nit")
            String nit,

            @JsonProperty("contract_id")
            Long contractId
    ) {}
}
