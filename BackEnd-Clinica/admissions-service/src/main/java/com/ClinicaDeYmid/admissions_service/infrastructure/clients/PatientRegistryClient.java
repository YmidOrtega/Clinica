package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDate;
import java.util.UUID;

@FeignClient(name = "patient-service")
interface PatientRegistryClient {

    @GetMapping("/api/v1/patients/{uuid}")
    RegisteredPayload findPatient(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/unidentified-patients/{uuid}")
    UnidentifiedPayload findUnidentified(@PathVariable("uuid") UUID uuid);

    @PostMapping("/api/v1/unidentified-patients")
    UnidentifiedPayload registerUnidentified(@RequestBody UnidentifiedRegistration registration);

    @PostMapping("/api/v1/patients/{uuid}/death")
    RegisteredPayload recordDeath(@PathVariable("uuid") UUID uuid,
                                  @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                                  @RequestBody DeathRecord death);

    @PostMapping("/api/v1/unidentified-patients/{uuid}/death")
    UnidentifiedPayload recordUnidentifiedDeath(@PathVariable("uuid") UUID uuid,
                                                @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                                                @RequestBody DeathRecord death);

    record UnidentifiedRegistration(String sex, Integer estimatedBirthYear, String description) {
    }

    record DeathRecord(LocalDate dateOfDeath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisteredPayload(UUID uuid, long version, Document document, Demographics demographics, Status status,
                             Affiliation affiliation) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Document(String type, String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Demographics(String firstNames, String lastNames, LocalDate birthDate, String sex) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code, LocalDate dateOfDeath) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Affiliation(String regime, String payerUuid) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnidentifiedPayload(UUID uuid, long version, String code, String sex, int estimatedBirthYear, Status status) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code, UUID identifiedPatientUuid, LocalDate dateOfDeath) {
        }
    }
}
