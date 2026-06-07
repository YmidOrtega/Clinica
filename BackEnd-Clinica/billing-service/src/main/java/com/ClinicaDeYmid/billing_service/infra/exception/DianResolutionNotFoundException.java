package com.ClinicaDeYmid.billing_service.infra.exception;

import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;

public class DianResolutionNotFoundException extends RuntimeException {
    public DianResolutionNotFoundException(Long id) {
        super("Resolución DIAN con ID " + id + " no encontrada.");
    }
    public DianResolutionNotFoundException(DocumentType documentType) {
        super("No existe una resolución DIAN vigente para el tipo de documento: " + documentType.name());
    }
}
