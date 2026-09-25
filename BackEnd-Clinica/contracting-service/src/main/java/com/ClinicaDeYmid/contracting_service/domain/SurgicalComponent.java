package com.ClinicaDeYmid.contracting_service.domain;

public enum SurgicalComponent {

    SURGEON("Honorarios del cirujano"),
    ANESTHESIOLOGIST("Honorarios del anestesiólogo"),
    ASSISTANT("Honorarios del ayudante"),
    OPERATING_ROOM("Derechos de sala"),
    MATERIALS("Materiales de sutura y curación");

    private final String label;

    SurgicalComponent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
