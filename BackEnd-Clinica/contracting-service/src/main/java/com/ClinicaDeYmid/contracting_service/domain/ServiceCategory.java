package com.ClinicaDeYmid.contracting_service.domain;

public enum ServiceCategory {

    CONSULTATION("Consulta"),
    PROCEDURE("Procedimiento"),
    SURGERY("Cirugía"),
    LABORATORY("Laboratorio"),
    IMAGING("Imágenes diagnósticas"),
    HOSPITALIZATION("Estancia"),
    SUPPLY("Insumo"),
    MEDICATION("Medicamento"),
    TRANSPORT("Traslado"),
    OTHER("Otro");

    private final String label;

    ServiceCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
