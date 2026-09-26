package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.application.clinical.CareRecord;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFacts;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcClinicalFacts implements ClinicalFacts {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcClinicalFacts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(ClinicalFact fact) {
        switch (fact) {
            case ClinicalFact.EncounterOpened opened -> jdbc.update("""
                    INSERT IGNORE INTO clinical_encounters (encounter_id, admission_uuid, patient_uuid, encounter_type,
                                                            opened_at, service_code, modality, service_group)
                    VALUES (:id, :admission, :patient, :type, :openedAt, :service, :modality, :group)""",
                    new MapSqlParameterSource()
                            .addValue("id", opened.encounterId().toString())
                            .addValue("admission", text(opened.admissionUuid()))
                            .addValue("patient", opened.patientUuid().toString())
                            .addValue("type", opened.encounterType())
                            .addValue("openedAt", Timestamp.from(opened.openedAt()))
                            .addValue("service", opened.careSetting() == null ? null : opened.careSetting().serviceCode())
                            .addValue("modality", opened.careSetting() == null ? null : opened.careSetting().modality())
                            .addValue("group", opened.careSetting() == null ? null : opened.careSetting().serviceGroup()));
            case ClinicalFact.NoteSigned signed -> {
                int inserted = jdbc.update("""
                        INSERT IGNORE INTO clinical_notes (note_id, encounter_id, admission_uuid, note_type, care_occurred_at,
                                                           purpose, cause, voided)
                        VALUES (:id, :encounter, :admission, :type, :occurredAt, :purpose, :cause, FALSE)""",
                        new MapSqlParameterSource()
                                .addValue("id", signed.noteId().toString())
                                .addValue("encounter", signed.encounterId().toString())
                                .addValue("admission", text(signed.admissionUuid()))
                                .addValue("type", signed.noteType())
                                .addValue("occurredAt", Timestamp.from(signed.careOccurredAt()))
                                .addValue("purpose", signed.purpose())
                                .addValue("cause", signed.cause()));
                if (inserted == 1) {
                    int position = 1;
                    for (ClinicalFact.CodedDiagnosis diagnosis : signed.diagnoses()) {
                        jdbc.update("""
                                INSERT INTO clinical_note_diagnoses (note_id, position, code, role, type)
                                VALUES (:note, :position, :code, :role, :type)""",
                                new MapSqlParameterSource("note", signed.noteId().toString())
                                        .addValue("position", position++)
                                        .addValue("code", diagnosis.code())
                                        .addValue("role", diagnosis.role())
                                        .addValue("type", diagnosis.type()));
                    }
                }
            }
            case ClinicalFact.NoteVoided voided -> jdbc.update(
                    "UPDATE clinical_notes SET voided = TRUE WHERE note_id = :id",
                    new MapSqlParameterSource("id", voided.noteId().toString()));
            case ClinicalFact.EncounterClosed closed -> jdbc.update(
                    "UPDATE clinical_encounters SET closed_at = :closedAt WHERE encounter_id = :id AND closed_at IS NULL",
                    new MapSqlParameterSource("id", closed.encounterId().toString())
                            .addValue("closedAt", Timestamp.from(closed.closedAt())));
        }
    }

    @Override
    public CareRecord ofAdmission(UUID admissionUuid) {
        MapSqlParameterSource admission = new MapSqlParameterSource("admission", admissionUuid.toString());
        List<CareRecord.Encounter> encounters = jdbc.query("""
                SELECT encounter_id, encounter_type, opened_at, closed_at, service_code, modality, service_group
                FROM clinical_encounters WHERE admission_uuid = :admission""", admission, (row, index) ->
                new CareRecord.Encounter(UUID.fromString(row.getString("encounter_id")), row.getString("encounter_type"),
                        row.getTimestamp("opened_at").toInstant(), instant(row.getTimestamp("closed_at")),
                        row.getString("service_code") == null ? null : new ClinicalFact.CareSetting(
                                row.getString("service_code"), row.getString("modality"), row.getString("service_group"))));
        Map<String, List<ClinicalFact.CodedDiagnosis>> diagnoses = new HashMap<>();
        jdbc.query("""
                SELECT d.note_id, d.code, d.role, d.type FROM clinical_note_diagnoses d
                JOIN clinical_notes n ON n.note_id = d.note_id
                WHERE n.admission_uuid = :admission AND NOT n.voided ORDER BY d.note_id, d.position""", admission, row -> {
            diagnoses.computeIfAbsent(row.getString("note_id"), key -> new ArrayList<>()).add(
                    new ClinicalFact.CodedDiagnosis(row.getString("code"), row.getString("role"), row.getString("type")));
        });
        List<CareRecord.Note> notes = jdbc.query("""
                SELECT note_id, encounter_id, note_type, care_occurred_at, purpose, cause FROM clinical_notes
                WHERE admission_uuid = :admission AND NOT voided""", admission, (row, index) ->
                new CareRecord.Note(UUID.fromString(row.getString("note_id")), UUID.fromString(row.getString("encounter_id")),
                        row.getString("note_type"), row.getTimestamp("care_occurred_at").toInstant(),
                        row.getString("purpose"), row.getString("cause"),
                        diagnoses.getOrDefault(row.getString("note_id"), List.of())));
        return new CareRecord(encounters, notes);
    }

    private static String text(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
