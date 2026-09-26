package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.UUID;

@FeignClient(name = "patient-service")
interface PatientRegistryClient {

    @GetMapping("/api/v1/patients/{uuid}")
    RegisteredPayload findPatient(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/unidentified-patients/{uuid}")
    UnidentifiedPayload findUnidentified(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisteredPayload(UUID uuid, Document document, Demographics demographics, Affiliation affiliation,
                             Residence residence) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(String type, String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Demographics(String firstNames, String lastNames, LocalDate birthDate, String sex,
                            String countryOfOriginCode) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Affiliation(String regime, String affiliateType) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Residence(String municipalityCode, String zone) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnidentifiedPayload(UUID uuid, String code, String sex, int estimatedBirthYear) {
    }
}
