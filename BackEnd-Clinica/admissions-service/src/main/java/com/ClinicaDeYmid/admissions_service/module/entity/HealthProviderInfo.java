package com.ClinicaDeYmid.admissions_service.module.entity;


import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@AllArgsConstructor
public class HealthProviderInfo {
    private String healthProviderNit;
    private Long contractId;
}
