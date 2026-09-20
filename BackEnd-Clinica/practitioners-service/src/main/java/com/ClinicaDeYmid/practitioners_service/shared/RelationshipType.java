package com.ClinicaDeYmid.practitioners_service.shared;

public enum RelationshipType {

    STAFF("Planta"),
    CONTRACTOR("Prestación de servicios"),
    EXTERNAL("Externo");

    private final String label;

    RelationshipType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
