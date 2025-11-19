package com.ClinicaDeYmid.suppliers_service.module.service.validation;

import lombok.Getter;

import java.util.List;

@Getter
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }
}