package com.ClinicaDeYmid.ai_assistant_service.module.feignclient;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "admissions-service", path = "/api/v1/attentions")
public interface AdmissionsClient {

    @PostMapping
    AttentionResponseDto createAttention(@RequestBody AttentionRequestDto request);

    @GetMapping("/{id}")
    AttentionResponseDto getAttentionById(@PathVariable("id") Long id);

    @GetMapping("/patient/{patientId}")
    Object getAttentionsByPatientId(@PathVariable("patientId") Long patientId);
}
