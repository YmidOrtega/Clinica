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

    default List<Diagnosis> diagnoses() {
        return List.of();
    }

    default NoteContent withDiagnoses(List<Diagnosis> resolved) {
        return this;
    }

    static CareReason careReasonOf(NoteContent content) {
        return switch (content) {
            case Admission admission -> admission.careReason();
            case Progress progress -> progress.careReason();
            case Consultation consultation -> consultation.careReason();
            case Discharge discharge -> discharge.careReason();
            case null, default -> null;
        };
    }

    record Admission(String chiefComplaint, String currentIllness, String physicalExam, String assessment, String plan,
                     List<Diagnosis> diagnoses,
                     CareReason careReason) implements NoteContent {

        public Admission {
            chiefComplaint = optional(chiefComplaint, "chiefComplaint", SHORT);
            currentIllness = optional(currentIllness, "currentIllness", LONG);
            physicalExam = optional(physicalExam, "physicalExam", LONG);
            assessment = optional(assessment, "assessment", LONG);
            plan = optional(plan, "plan", LONG);
            diagnoses = Diagnosis.normalize(diagnoses);
        }

        public Admission(String chiefComplaint, String currentIllness, String physicalExam, String assessment, String plan, List<Diagnosis> diagnoses) {
            this(chiefComplaint, currentIllness, physicalExam, assessment, plan, diagnoses, null);
        }

        @Override
        public NoteContent withDiagnoses(List<Diagnosis> resolved) {
            return new Admission(chiefComplaint, currentIllness, physicalExam, assessment, plan, resolved, careReason);
        }

        @Override
        public NoteType type() {
            return NoteType.ADMISSION;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(chiefComplaint, "chiefComplaint").check(currentIllness, "currentIllness")
                    .check(physicalExam, "physicalExam").check(assessment, "assessment").check(plan, "plan")
                    .checkPrincipal(diagnoses).fields();
        }
    }

    record Progress(String subjective, String objective, String assessment, String plan, List<Diagnosis> diagnoses,
                     CareReason careReason)
            implements NoteContent {

        public Progress {
            subjective = optional(subjective, "subjective", LONG);
            objective = optional(objective, "objective", LONG);
            assessment = optional(assessment, "assessment", LONG);
            plan = optional(plan, "plan", LONG);
            diagnoses = Diagnosis.normalize(diagnoses);
        }

        public Progress(String subjective, String objective, String assessment, String plan, List<Diagnosis> diagnoses) {
            this(subjective, objective, assessment, plan, diagnoses, null);
        }

        @Override
        public NoteContent withDiagnoses(List<Diagnosis> resolved) {
            return new Progress(subjective, objective, assessment, plan, resolved, careReason);
        }

        @Override
        public NoteType type() {
            return NoteType.PROGRESS;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(subjective, "subjective").check(objective, "objective")
                    .check(assessment, "assessment").check(plan, "plan").checkPrincipalIfAny(diagnoses).fields();
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

    record Consultation(String specialty, String reason, String findings, String recommendations, List<Diagnosis> diagnoses,
                     CareReason careReason)
            implements NoteContent {

        public Consultation {
            specialty = optional(specialty, "specialty", 100);
            reason = optional(reason, "reason", SHORT);
            findings = optional(findings, "findings", LONG);
            recommendations = optional(recommendations, "recommendations", LONG);
            diagnoses = Diagnosis.normalize(diagnoses);
        }

        public Consultation(String specialty, String reason, String findings, String recommendations, List<Diagnosis> diagnoses) {
            this(specialty, reason, findings, recommendations, diagnoses, null);
        }

        @Override
        public NoteContent withDiagnoses(List<Diagnosis> resolved) {
            return new Consultation(specialty, reason, findings, recommendations, resolved, careReason);
        }

        @Override
        public NoteType type() {
            return NoteType.CONSULTATION;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(specialty, "specialty").check(reason, "reason")
                    .check(findings, "findings").check(recommendations, "recommendations").checkPrincipalIfAny(diagnoses).fields();
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
                     String followUp, List<Diagnosis> diagnoses,
                     CareReason careReason) implements NoteContent {

        public Discharge {
            admissionSummary = optional(admissionSummary, "admissionSummary", LONG);
            evolutionSummary = optional(evolutionSummary, "evolutionSummary", LONG);
            dischargeCondition = optional(dischargeCondition, "dischargeCondition", SHORT);
            recommendations = optional(recommendations, "recommendations", LONG);
            followUp = optional(followUp, "followUp", LONG);
            diagnoses = Diagnosis.normalize(diagnoses);
        }

        public Discharge(String admissionSummary, String evolutionSummary, String dischargeCondition, String recommendations, String followUp, List<Diagnosis> diagnoses) {
            this(admissionSummary, evolutionSummary, dischargeCondition, recommendations, followUp, diagnoses, null);
        }

        @Override
        public NoteContent withDiagnoses(List<Diagnosis> resolved) {
            return new Discharge(admissionSummary, evolutionSummary, dischargeCondition, recommendations, followUp, resolved, careReason);
        }

        @Override
        public NoteType type() {
            return NoteType.DISCHARGE;
        }

        @Override
        public List<String> missingFields() {
            return Missing.of().check(admissionSummary, "admissionSummary").check(evolutionSummary, "evolutionSummary")
                    .check(dischargeCondition, "dischargeCondition").check(recommendations, "recommendations")
                    .check(followUp, "followUp").checkPrincipal(diagnoses).fields();
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

        Missing checkPrincipal(List<Diagnosis> diagnoses) {
            if (!Diagnosis.hasPrincipal(diagnoses)) {
                fields.add("diagnoses.principal");
            }
            return this;
        }

        Missing checkPrincipalIfAny(List<Diagnosis> diagnoses) {
            return diagnoses.isEmpty() ? this : checkPrincipal(diagnoses);
        }

        List<String> fields() {
            return List.copyOf(fields);
        }
    }
}
