package com.ClinicaDeYmid.contracting_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "patient-service")
interface PatientDirectoryClient {

    @PostMapping("/api/v1/patients/search")
    Page search(@RequestBody Search request);

    record Search(Document document) {
    }

    record Document(String type, String number) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(List<Match> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Match(UUID uuid) {
    }
}
