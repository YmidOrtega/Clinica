package com.ClinicaDeYmid.clinical_history_service.domain.note;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.LONG;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.SHORT;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText.optional;

public sealed interface NoteContent {

    NoteType type();

    List<String> missingFields();

    record Admission(String chiefComplaint, String currentIllness, String physicalExam, String assessment, String plan)
            implements NoteContent {

        public Admission {
            chiefComplaint = optional(chiefComplaint, "chiefComplaint", SHORT);
            currentIllness = optional(currentIllness, "currentIllness", LONG);
            physicalExam = optional(physicalExam, "physicalExam", LONG);
            assessment = optional(assessment, "assessment", LONG);
            plan = optional(plan, "plan", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.ADMISSION;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(chiefComplaint, "chiefComplaint").check(currentIllness, "currentIllness")
                    .check(physicalExam, "physicalExam").check(assessment, "assessment").check(plan, "plan").fields();
        }
    }

    record Progress(String subjective, String objective, String assessment, String plan) implements NoteContent {

        public Progress {
            subjective = optional(subjective, "subjective", LONG);
            objective = optional(objective, "objective", LONG);
            assessment = optional(assessment, "assessment", LONG);
            plan = optional(plan, "plan", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.PROGRESS;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(subjective, "subjective").check(objective, "objective")
                    .check(assessment, "assessment").check(plan, "plan").fields();
        }
    }

    record Triage(TriageLevel level, String reason, String observations) implements NoteContent {

        public Triage {
            reason = optional(reason, "reason", SHORT);
            observations = optional(observations, "observations", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.TRIAGE;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(level, "level").check(reason, "reason").fields();
        }
    }

    record Consultation(String specialty, String reason, String findings, String recommendations) implements NoteContent {

        public Consultation {
            specialty = optional(specialty, "specialty", 100);
            reason = optional(reason, "reason", SHORT);
            findings = optional(findings, "findings", LONG);
            recommendations = optional(recommendations, "recommendations", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.CONSULTATION;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(specialty, "specialty").check(reason, "reason")
                    .check(findings, "findings").check(recommendations, "recommendations").fields();
        }
    }

    record Nursing(String observations, String careProvided) implements NoteContent {

        public Nursing {
            observations = optional(observations, "observations", LONG);
            careProvided = optional(careProvided, "careProvided", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.NURSING;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(observations, "observations").check(careProvided, "careProvided").fields();
        }
    }

    record Discharge(String admissionSummary, String evolutionSummary, String dischargeCondition, String recommendations,
                     String followUp) implements NoteContent {

        public Discharge {
            admissionSummary = optional(admissionSummary, "admissionSummary", LONG);
            evolutionSummary = optional(evolutionSummary, "evolutionSummary", LONG);
            dischargeCondition = optional(dischargeCondition, "dischargeCondition", SHORT);
            recommendations = optional(recommendations, "recommendations", LONG);
            followUp = optional(followUp, "followUp", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.DISCHARGE;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(admissionSummary, "admissionSummary").check(evolutionSummary, "evolutionSummary")
                    .check(dischargeCondition, "dischargeCondition").check(recommendations, "recommendations")
                    .check(followUp, "followUp").fields();
        }
    }

    record Addendum(UUID amendsNoteId, String text) implements NoteContent {

        public Addendum {
            text = optional(text, "text", LONG);
        }

        @Override
        public NoteType type() {
            return NoteType.ADDENDUM;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(amendsNoteId, "amendsNoteId").check(text, "text").fields();
        }
    }

    final class Missing {

        private final List<String> fields = new ArrayList<>();

        private Missing() {
        }

        static Missing of() {
            return new Missing();
        }

        Missing check(Object value, String field) {
            if (value == null) {
                fields.add(field);
            }
            return this;
        }

        List<String> fields() {
            return List.copyOf(fields);
        }
    }
}
