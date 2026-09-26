package com.ClinicaDeYmid.billing_service.domain;

public enum UncontractedCare {

    EMERGENCY("01", "ATENCION DE URGENCIAS"),
    ADRES_SOAT_OR_VOLUNTARY_PLAN("02", "ATENCION A CARGO DE ADRES O DE ASEGURADORA SOAT, PLANES VOLUNTARIOS DE SALUD"),
    COURT_ORDER("03", "ATENCION EN SALUD POR FALLOS DE TUTELA/ORDENES JUDICIALES"),
    PORTABILITY("04", "ATENCION EN SALUD POR PORTABILIDAD O EN LOS CASOS DE ASIGNACION MASIVA DE AFILIADOS"),
    EXCEPTIONAL("05", "ATENCION EN SALUD EN CASOS EXCEPCIONALES POR COTIZACIONES O AUTORIZACIONES SIN CONTRATO"),
    ORGAN_RECOVERY("06", "GESTION RECUPERACION DE ORGANOS PARA TRASPLANTE"),
    PRIVATE_PATIENT("07", "ATENCION A PACIENTES PARTICULARES");

    private final String sisproCode;
    private final String label;

    UncontractedCare(String sisproCode, String label) {
        this.sisproCode = sisproCode;
        this.label = label;
    }

    public String sisproCode() {
        return sisproCode;
    }

    public String label() {
        return label;
    }
}
