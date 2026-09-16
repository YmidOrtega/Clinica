package com.ClinicaDeYmid.contracting_service.domain;

public enum MemberVerification {

    MATCHED("Identificado en el registro de pacientes"),
    UNMATCHED("Sin paciente registrado con ese documento"),
    UNVERIFIED("No se pudo verificar contra el registro de pacientes");

    private final String label;

    MemberVerification(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
