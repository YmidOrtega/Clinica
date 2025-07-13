package com.ClinicaDeYmid.clients_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import jakarta.validation.constraints.*;

public record CreateHealthProviderDto(
        @Schema(description = "Social reason (legal name) of the provider", example = "Salud Total S.A.", required = true)
        @NotBlank(message = "La razón social es obligatoria.")
        @Size(min = 2, max = 200, message = "La razón social debe tener entre 2 y 200 caracteres.")
        String socialReason,

        @Schema(description = "NIT (tax identification number)", example = "900123456-7", required = true)
        @NotNull(message = "El NIT es obligatorio.")
        Nit nit,

        @Schema(description = "Type of provider", example = "EPS", required = true, implementation = TypeProvider.class)
        @NotNull(message = "El tipo de proveedor es obligatorio.")
        TypeProvider typeProvider,

        @Schema(description = "Address of the provider", example = "Calle 123 #45-67", required = false)
        @Size(max = 500, message = "La dirección no puede exceder los 500 caracteres.")
        String address,

        @Schema(description = "Phone number (7 to 20 digits, may include +, -, spaces, parentheses)", example = "+5712345678", required = false)
        @Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$",
                message = "El teléfono debe tener un formato válido (7 a 20 dígitos, puede incluir +, -, espacios, paréntesis).")
        String phone,

        @Schema(description = "Validity year", example = "2024", required = true)
        @NotNull(message = "El año de validez es obligatorio.")
        @Min(value = 1900, message = "El año de validez debe ser posterior a 1900.")
        @Max(value = 2100, message = "El año de validez debe ser anterior a 2100.")
        Integer yearOfValidity,

        @Schema(description = "Year of completion", example = "2026", required = true)
        @NotNull(message = "El año de finalización es obligatorio.")
        @Min(value = 1900, message = "El año de finalización debe ser posterior a 1900.")
        @Max(value = 2100, message = "El año de finalización debe ser anterior a 2100.")
        Integer yearCompletion
) {
    @AssertTrue(message = "El año de finalización debe ser igual o posterior al año de validez.")
    public boolean isYearCompletionValid() {
        if (yearCompletion == null || yearOfValidity == null) {
            return true;
        }
        return yearCompletion >= yearOfValidity;
    }
}
