package com.ClinicaDeYmid.contracting_service.domain;

public enum PayerType {

    EPS("Entidad Promotora de Salud"),
    IPS("Institución Prestadora de Servicios de Salud"),
    ARL("Administradora de Riesgos Laborales"),
    PREPAID_MEDICINE("Medicina prepagada"),
    COMPLEMENTARY_PLAN("Plan complementario"),
    HEALTH_POLICY("Póliza de salud"),
    STUDENT_POLICY("Póliza estudiantil"),
    SPECIAL_REGIME("Régimen especial"),
    OTHER("Otro");

    private final String label;

    PayerType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
