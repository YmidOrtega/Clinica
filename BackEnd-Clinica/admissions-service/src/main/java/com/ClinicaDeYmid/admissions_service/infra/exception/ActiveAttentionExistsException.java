package com.ClinicaDeYmid.admissions_service.infra.exception;

public class ActiveAttentionExistsException extends ValidationException {
    private final Long patientId;
    private final Long existingAttentionId;

    public ActiveAttentionExistsException(Long patientId, Long existingAttentionId) {
        super(String.format("Patient with ID: %d already has an active attention with ID: %d",
                patientId, existingAttentionId));
        this.patientId = patientId;
        this.existingAttentionId = existingAttentionId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getExistingAttentionId() {
        return existingAttentionId;
    }
}
