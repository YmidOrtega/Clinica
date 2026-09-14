package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.CodedConcept;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record Diagnosis(String code, Role role, Type type, String display, String catalogVersion) {

    public enum Role {
        PRINCIPAL,
        RELATED
    }

    public enum Type {
        IMPRESSION,
        CONFIRMED_NEW,
        CONFIRMED_REPEATED
    }

    static final int MAX_PER_NOTE = 20;

    private static final Pattern CODE = Pattern.compile("^[A-Z][0-9]{2}[0-9X]$");

    public Diagnosis {
        code = code == null ? null : code.strip().replace(".", "").toUpperCase();
        if (code == null || !CODE.matcher(code).matches()) {
            throw new ClinicalException.InvalidData("diagnoses.code", "debe ser un código CIE-10 de cuatro caracteres, por ejemplo I109");
        }
        if (role == null) {
            throw new ClinicalException.InvalidData("diagnoses.role", "es obligatorio");
        }
        if (type == null) {
            throw new ClinicalException.InvalidData("diagnoses.type", "es obligatorio");
        }
    }

    public Diagnosis resolvedAs(CodedConcept concept) {
        return new Diagnosis(concept.code(), role, type, concept.display(), concept.catalogVersion());
    }

    static List<Diagnosis> normalize(List<Diagnosis> diagnoses) {
        List<Diagnosis> list = diagnoses == null ? List.of() : List.copyOf(diagnoses);
        if (list.size() > MAX_PER_NOTE) {
            throw new ClinicalException.InvalidData("diagnoses", "no puede tener más de " + MAX_PER_NOTE + " diagnósticos");
        }
        Set<String> codes = new HashSet<>();
        for (Diagnosis diagnosis : list) {
            if (!codes.add(diagnosis.code())) {
                throw new ClinicalException.InvalidData("diagnoses", "repite el código " + diagnosis.code());
            }
        }
        if (list.stream().filter(diagnosis -> diagnosis.role() == Role.PRINCIPAL).count() > 1) {
            throw new ClinicalException.InvalidData("diagnoses", "solo puede tener un diagnóstico principal");
        }
        return list;
    }

    static boolean hasPrincipal(List<Diagnosis> diagnoses) {
        return diagnoses.stream().anyMatch(diagnosis -> diagnosis.role() == Role.PRINCIPAL);
    }
}
