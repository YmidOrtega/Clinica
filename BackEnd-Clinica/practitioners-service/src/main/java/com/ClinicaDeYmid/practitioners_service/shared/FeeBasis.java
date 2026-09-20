package com.ClinicaDeYmid.practitioners_service.shared;

public enum FeeBasis {

    HOURLY("Por hora"),
    PER_SHIFT("Por turno"),
    PER_PROCEDURE("Por procedimiento");

    private final String label;

    FeeBasis(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
