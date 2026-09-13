package com.ClinicaDeYmid.patient_service.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PatientRows {

    private PatientRows() {
    }

    public static Map<String, String> valid() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("uuid", "'3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d'");
        row.put("version", "0");
        row.put("document_type", "'CEDULA_DE_CIUDADANIA'");
        row.put("document_number", "'1098765432'");
        row.put("first_names", "'Ana'");
        row.put("last_names", "'Restrepo'");
        row.put("birth_date", "'1990-01-01'");
        row.put("sex", "'FEMALE'");
        row.put("country_of_origin", "'CO'");
        row.put("disability", "'NONE'");
        row.put("mobile", "'3001234567'");
        row.put("health_regime", "'CONTRIBUTORY'");
        row.put("affiliate_type", "'HOLDER'");
        row.put("health_provider_nit", "'900123456-7'");
        row.put("residence_department", "'Santander'");
        row.put("residence_municipality", "'Bucaramanga'");
        row.put("residence_zone", "'URBAN'");
        row.put("residence_address", "'Calle 45'");
        row.put("status", "'ACTIVE'");
        row.put("created_at", "NOW(6)");
        row.put("updated_at", "NOW(6)");
        return row;
    }

    public static void insert(JdbcTemplate jdbc, Map<String, String> row) {
        jdbc.update("INSERT INTO patients (" + String.join(", ", row.keySet()) + ") VALUES (" + String.join(", ", row.values()) + ")");
    }
}
