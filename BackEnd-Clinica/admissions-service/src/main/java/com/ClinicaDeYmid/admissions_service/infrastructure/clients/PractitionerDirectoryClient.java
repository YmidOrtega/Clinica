package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "practitioners-service")
interface PractitionerDirectoryClient {

    @GetMapping("/api/v1/practitioners/{uuid}")
    PractitionerPayload find(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PractitionerPayload(UUID uuid, long version, String fullName, Registration registration, Status status,
                               List<Specialty> specialties, Account account) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Registration(String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Specialty(String specialtyName, String subSpecialtyName, boolean principal) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Account(UUID userUuid) {
        }
    }
}
