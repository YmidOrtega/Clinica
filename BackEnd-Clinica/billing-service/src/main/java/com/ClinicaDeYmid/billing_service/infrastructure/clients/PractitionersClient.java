package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "practitioners-service")
interface PractitionersClient {

    @GetMapping("/api/v1/practitioners/{uuid}")
    PractitionerPayload practitioner(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/practitioners/{uuid}/fee-agreements/in-force")
    FeeAgreementPayload feesInForce(@PathVariable("uuid") UUID uuid, @RequestParam("on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeeAgreementPayload(UUID uuid, String basis, List<ProcedureFee> procedures) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ProcedureFee(String serviceCode, BigDecimal amount) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PractitionerPayload(UUID uuid, String fullName, Registration registration, Status status,
                               Document document) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(String type, String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Registration(String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code, boolean attends) {
        }
    }
}
