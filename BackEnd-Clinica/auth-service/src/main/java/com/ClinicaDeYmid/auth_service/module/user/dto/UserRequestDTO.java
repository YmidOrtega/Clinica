package com.ClinicaDeYmid.auth_service.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.Set;

public record UserRequestDTO(
        @NotBlank String username,
        @NotBlank @Past LocalDate birthDate,
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotBlank String phoneNumber,
        @NotEmpty Set<Long> roleIds
) {}
