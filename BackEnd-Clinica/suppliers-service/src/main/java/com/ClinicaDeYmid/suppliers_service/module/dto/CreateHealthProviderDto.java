package com.ClinicaDeYmid.suppliers_service.module.dto;

import jakarta.validation.constraints.*;

public record CreateHealthProviderDto(
        @NotBlank(message = "La razón social es obligatoria")
        @Size(min = 2, max = 200, message = "La razón social debe tener entre 2 y 200 caracteres")
        String socialReason,
        @NotBlank(message = "El NIT es obligatorio")
        @Pattern(regexp = "^[0-9]{6,12}$", message = "El NIT debe contener entre 6 y 12 dígitos")
        String nit,

        @Size(max = 100, message = "El contrato no puede exceder 100 caracteres")
        String contract,

        @Size(min = 3, max = 100, message = "El número de contrato debe tener entre 3 y 100 caracteres")
        String numberContract,

        @Pattern(regexp = "^(EPS|ARL|PREPAGADA|SUBSIDIADO|CONTRIBUTIVO)$",
                message = "El tipo debe ser: EPS, ARL, PREPAGADA, SUBSIDIADO o CONTRIBUTIVO")
        String type,

        @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
        String address,

        @Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$",
                message = "El teléfono debe tener un formato válido")
        String phone,

        @Min(value = 2000, message = "El año de validez debe ser mayor o igual a 2000")
        @Max(value = 2050, message = "El año de validez debe ser menor o igual a 2050")
        Integer yearOfValidity,

        @Min(value = 2000, message = "El año de finalización debe ser mayor o igual a 2000")
        @Max(value = 2050, message = "El año de finalización debe ser menor o igual a 2050")
        Integer yearCompletion
) {

    @AssertTrue(message = "El año de finalización debe ser mayor o igual al año de validez")
    public boolean isYearCompletionValid() {

        if (yearCompletion == null || yearOfValidity == null) {
            return true;
        }
        return yearCompletion >= yearOfValidity;
    }
}