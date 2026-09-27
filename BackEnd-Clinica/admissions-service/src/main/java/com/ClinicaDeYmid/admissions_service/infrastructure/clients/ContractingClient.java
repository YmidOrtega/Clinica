package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "contracting-service")
interface ContractingClient {

    @GetMapping("/api/v1/contracts")
    List<ContractPayload> contractsInForce(@RequestParam("payer") UUID payer,
                                           @RequestParam("inForceOn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inForceOn);

    @GetMapping("/api/v1/capitated-members/coverage")
    List<CoveragePayload> capitatedCoverage(@RequestParam("documentType") String documentType,
                                            @RequestParam("documentNumber") String documentNumber,
                                            @RequestParam("on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContractPayload(UUID uuid, String number, String name, String modality, UUID payerUuid, Status status) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code) {
        }

        boolean active() {
            return status != null && "ACTIVE".equals(status.code());
        }

        boolean capitated() {
            return "CAPITATION".equals(modality);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoveragePayload(UUID contractUuid, String contractNumber, UUID payerUuid) {
    }
}
