package com.ClinicaDeYmid.contracting_service.domain;

public enum SurgicalBasis {

    UVR("Unidades de valor relativo"),
    SURGICAL_GROUP("Grupo quirúrgico");

    private final String label;

    SurgicalBasis(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
