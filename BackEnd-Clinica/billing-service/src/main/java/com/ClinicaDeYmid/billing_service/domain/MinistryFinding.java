package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MinistryFinding {

    public static final String REJECTION = "RECHAZADO";

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "observations", length = 2000)
    private String observations;

    @Column(name = "source_path", length = 500)
    private String path;

    @Column(name = "source", length = 60)
    private String source;

    protected MinistryFinding() {
    }

    public MinistryFinding(String kind, String code, String description, String observations, String path,
                           String source) {
        this.kind = truncated(kind == null || kind.isBlank() ? "DESCONOCIDO" : kind, 30);
        this.code = truncated(code == null || code.isBlank() ? "SIN_CODIGO" : code, 20);
        this.description = truncated(description, 2000);
        this.observations = truncated(observations, 2000);
        this.path = truncated(path, 500);
        this.source = truncated(source, 60);
    }

    private static String truncated(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    public boolean rejects() {
        return kind.toUpperCase(java.util.Locale.ROOT).startsWith("RECHAZ");
    }

    public String kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public String observations() {
        return observations;
    }

    public String path() {
        return path;
    }

    public String source() {
        return source;
    }
}
