package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntries;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptedContentUnreadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcLedgerEntries implements LedgerEntries {

    private static final Logger log = LoggerFactory.getLogger(JdbcLedgerEntries.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcClinicalNotes notes;

    JdbcLedgerEntries(NamedParameterJdbcTemplate jdbc, JdbcClinicalNotes notes) {
        this.jdbc = jdbc;
        this.notes = notes;
    }

    @Override
    public List<LedgerEntry> recordedFor(UUID patientUuid) {
        MapSqlParameterSource patient = new MapSqlParameterSource("patientUuid", patientUuid.toString());
        List<LedgerEntry> entries = new ArrayList<>();
        jdbc.query(JdbcEncounters.SELECT + " WHERE e.patient_uuid = :patientUuid", patient, JdbcEncounters::toEncounter)
                .forEach(encounter -> entries.add(new LedgerEntry.EncounterOpened(encounter)));
        jdbc.query("""
                SELECT c.encounter_id, c.closed_at, c.closed_by, c.closed_by_role
                FROM clinical_ledger.encounter_closures c
                JOIN clinical_ledger.encounters e ON e.id = c.encounter_id
                WHERE e.patient_uuid = :patientUuid""", patient,
                (row, index) -> new EncounterClosure(Rows.uuid(row, "encounter_id"), Rows.instant(row, "closed_at"),
                        Rows.clinician(row, "closed_by", "closed_by_role")))
                .forEach(closure -> entries.add(new LedgerEntry.EncounterClosed(patientUuid, closure)));
        entries.addAll(jdbc.query(JdbcClinicalNotes.SELECT_NOTE
                        + " WHERE encounter_id IN (SELECT id FROM clinical_ledger.encounters WHERE patient_uuid = :patientUuid)",
                patient, (row, index) -> {
                    try {
                        return new LedgerEntry.NoteSigned(patientUuid, notes.toNote(row, index));
                    } catch (EncryptedContentUnreadableException unreadable) {
                        return unreadable(patientUuid, EntryType.NOTE_SIGNED, Rows.uuid(row, "id"), unreadable);
                    }
                }));
        entries.addAll(jdbc.query(JdbcClinicalNotes.SELECT_VOID + """
                 JOIN clinical_ledger.notes n ON n.id = v.note_id
                 JOIN clinical_ledger.encounters e ON e.id = n.encounter_id
                WHERE e.patient_uuid = :patientUuid""", patient, (row, index) -> {
            try {
                return new LedgerEntry.NoteVoided(patientUuid, notes.toVoid(row, index));
            } catch (EncryptedContentUnreadableException unreadable) {
                return unreadable(patientUuid, EntryType.NOTE_VOIDED, Rows.uuid(row, "note_id"), unreadable);
            }
        }));
        return entries;
    }

    private static LedgerEntry unreadable(UUID patientUuid, EntryType type, UUID entryId, EncryptedContentUnreadableException cause) {
        log.error("Clinical record entry {} {} of patient {} cannot be decrypted: {}", type, entryId, patientUuid, cause.getMessage());
        return new LedgerEntry.Unreadable(patientUuid, type, entryId);
    }
}
