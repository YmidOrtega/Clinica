package com.ClinicaDeYmid.contracting_service.domain;

public enum CoveragePlan {

    MAXIMUM_BUDGET("02", "Presupuesto máximo"),
    UNINSURED_VEHICLE_EPS("03", "Prima EPS / EOC, no asegurados SOAT"),
    SOAT_POLICY("04", "Cobertura póliza SOAT"),
    ARL("05", "Cobertura ARL"),
    ADRES("06", "Cobertura ADRES"),
    PUBLIC_HEALTH("07", "Cobertura salud pública"),
    TERRITORIAL_ENTITY("08", "Cobertura entidad territorial, recursos de oferta"),
    MIGRANT_EMERGENCY("09", "Urgencias población migrante"),
    COMPLEMENTARY_PLAN("10", "Plan complementario en salud"),
    PREPAID_MEDICINE("11", "Plan medicina prepagada"),
    HEALTH_POLICY("12", "Otras pólizas en salud"),
    SPECIAL_REGIME("13", "Cobertura régimen especial o excepción"),
    PRISON_FUND("14", "Cobertura Fondo Nacional de Salud de las Personas Privadas de la Libertad"),
    PRIVATE("15", "Particular"),
    UPC_CONTRIBUTORY("16", "Plan de beneficios en salud financiado con UPC contributivo"),
    UPC_SUBSIDIZED("17", "Plan de beneficios en salud financiado con UPC subsidiado");

    private final String sisproCode;
    private final String label;

    CoveragePlan(String sisproCode, String label) {
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
