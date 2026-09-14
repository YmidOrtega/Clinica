package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.CodedConcept;

import java.time.LocalDate;
import java.util.regex.Pattern;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.LONG;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.SHORT;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.optional;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.required;

public sealed interface ListItemDetails {

    ListCategory category();

    default String conditionCode() {
        return null;
    }

    default ListItemDetails withCondition(CodedConcept concept) {
        return this;
    }

    record Allergy(String substance, String reaction, Severity severity) implements ListItemDetails {

        public enum Severity {
            MILD,
            MODERATE,
            SEVERE,
            LIFE_THREATENING
        }

        public Allergy {
            substance = required(substance, "details.substance", 200);
            reaction = optional(reaction, "details.reaction", SHORT);
            if (severity == null) {
                throw new ClinicalException.InvalidData("details.severity", "es obligatorio");
            }
        }

        @Override
        public ListCategory category() {
            return ListCategory.ALLERGY;
        }
    }

    record ChronicCondition(String code, String display, String catalogVersion, String notes) implements ListItemDetails {

        private static final Pattern CODE = Pattern.compile("^[A-Z][0-9]{2}[0-9X]$");

        public ChronicCondition {
            code = code == null ? null : code.strip().replace(".", "").toUpperCase();
            if (code == null || !CODE.matcher(code).matches()) {
                throw new ClinicalException.InvalidData("details.code", "debe ser un código CIE-10 de cuatro caracteres");
            }
            notes = optional(notes, "details.notes", LONG);
        }

        @Override
        public ListCategory category() {
            return ListCategory.CHRONIC_CONDITION;
        }

        @Override
        public String conditionCode() {
            return code;
        }

        @Override
        public ListItemDetails withCondition(CodedConcept concept) {
            return new ChronicCondition(concept.code(), concept.display(), concept.catalogVersion(), notes);
        }
    }

    record CurrentMedication(String medication, String dose, String route, String frequency) implements ListItemDetails {

        public CurrentMedication {
            medication = required(medication, "details.medication", 200);
            dose = optional(dose, "details.dose", 100);
            route = optional(route, "details.route", 100);
            frequency = optional(frequency, "details.frequency", 100);
        }

        @Override
        public ListCategory category() {
            return ListCategory.CURRENT_MEDICATION;
        }
    }

    record FamilyHistory(String relationship, String condition) implements ListItemDetails {

        public FamilyHistory {
            relationship = required(relationship, "details.relationship", 100);
            condition = required(condition, "details.condition", SHORT);
        }

        @Override
        public ListCategory category() {
            return ListCategory.FAMILY_HISTORY;
        }
    }

    record PastHistory(Kind kind, String description, Integer year) implements ListItemDetails {

        public enum Kind {
            MEDICAL,
            SURGICAL,
            TRAUMATIC,
            TOXIC,
            GYNECO_OBSTETRIC,
            OTHER
        }

        public PastHistory {
            if (kind == null) {
                throw new ClinicalException.InvalidData("details.kind", "es obligatorio");
            }
            description = required(description, "details.description", SHORT);
            if (year != null && (year < 1900 || year > 2100)) {
                throw new ClinicalException.InvalidData("details.year", "no es un año válido");
            }
        }

        @Override
        public ListCategory category() {
            return ListCategory.PAST_HISTORY;
        }
    }

    record Vaccination(String vaccine, String dose, LocalDate appliedOn) implements ListItemDetails {

        public Vaccination {
            vaccine = required(vaccine, "details.vaccine", 200);
            dose = optional(dose, "details.dose", 50);
        }

        @Override
        public ListCategory category() {
            return ListCategory.VACCINATION;
        }
    }
}
