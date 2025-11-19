package com.ClinicaDeYmid.suppliers_service.module.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

public record DoctorAvailabilityResponseDTO(
        @Schema(description = "ID del doctor", example = "1")
        Long doctorId,

        @Schema(description = "Nombre completo del doctor", example = "Dr. Juan Pérez")
        String doctorFullName,

        @Schema(description = "Licencia médica", example = "MED-12345")
        String licenseNumber,

        @Schema(description = "Email", example = "juan.perez@example.com")
        String email,

        @Schema(description = "Teléfono", example = "+573011234567")
        String phoneNumber,

        @Schema(description = "¿Está disponible?", example = "true")
        Boolean available,

        @Schema(description = "Razón de no disponibilidad", example = "Doctor en vacaciones")
        String unavailabilityReason,

        @Schema(description = "Horarios disponibles en el día consultado")
        java.util.List<TimeSlotDTO> availableTimeSlots
) {}
