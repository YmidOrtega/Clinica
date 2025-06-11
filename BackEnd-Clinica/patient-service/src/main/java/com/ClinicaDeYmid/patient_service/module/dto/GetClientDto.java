package com.ClinicaDeYmid.patient_service.module.dto;

public record GetClientDto(
        String nit,
        String socialReason,
        String typeProvider
) {
}
