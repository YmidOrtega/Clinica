package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcClinicalNotes implements ClinicalNotes {

    static final String SELECT_NOTE = """
            SELECT id, encounter_id, content, author_uuid, author_role, author_email, occurred_at, recorded_at, extemporaneous
            FROM clinical_ledger.notes""";

    static final String SELECT_VOID = """
            SELECT v.note_id, v.reason, v.voided_by, v.voided_by_role, v.voided_at
            FROM clinical_ledger.note_voids v""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcClinicalNotes(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(SignedNote note) {
        jdbc.update("""
                INSERT INTO clinical_ledger.notes
                    (id, encounter_id, type, content, amends_note_id, author_uuid, author_role, author_email, occurred_at, recorded_at, extemporaneous)
                VALUES (:id, :encounterId, :type, :content, :amendsNoteId, :authorUuid, :authorRole, :authorEmail, :occurredAt, :recordedAt, :extemporaneous)""",
                new MapSqlParameterSource()
                        .addValue("id", note.id().toString())
                        .addValue("encounterId", note.encounterId().toString())
                        .addValue("type", note.type().name())
                        .addValue("content", NoteContentColumn.write(note.content()))
                        .addValue("amendsNoteId", note.amends().map(UUID::toString).orElse(null))
                        .addValue("authorUuid", note.author().uuid().toString())
                        .addValue("authorRole", note.author().role().name())
                        .addValue("authorEmail", note.signerEmail())
                        .addValue("occurredAt", Rows.timestamp(note.occurredAt()))
                        .addValue("recordedAt", Rows.timestamp(note.recordedAt()))
                        .addValue("extemporaneous", note.extemporaneous()));
    }

    @Override
    public Optional<SignedNote> find(UUID id) {
        return jdbc.query(SELECT_NOTE + " WHERE id = :id", new MapSqlParameterSource("id", id.toString()), JdbcClinicalNotes::toNote)
                .stream().findFirst();
    }

    @Override
    public List<SignedNote> ofEncounter(UUID encounterId) {
        return jdbc.query(SELECT_NOTE + " WHERE encounter_id = :encounterId ORDER BY recorded_at, id",
                new MapSqlParameterSource("encounterId", encounterId.toString()), JdbcClinicalNotes::toNote);
    }

    @Override
    public boolean addVoid(NoteVoid noteVoid) {
        try {
            return jdbc.update("""
                    INSERT INTO clinical_ledger.note_voids (note_id, reason, voided_by, voided_by_role, voided_at)
                    VALUES (:noteId, :reason, :voidedBy, :voidedByRole, :voidedAt)""",
                    new MapSqlParameterSource()
                            .addValue("noteId", noteVoid.noteId().toString())
                            .addValue("reason", noteVoid.reason())
                            .addValue("voidedBy", noteVoid.voidedBy().uuid().toString())
                            .addValue("voidedByRole", noteVoid.voidedBy().role().name())
                            .addValue("voidedAt", Rows.timestamp(noteVoid.voidedAt()))) == 1;
        } catch (DuplicateKeyException alreadyVoided) {
            return false;
        }
    }

    @Override
    public Optional<NoteVoid> voidOf(UUID noteId) {
        return jdbc.query(SELECT_VOID + " WHERE v.note_id = :noteId", new MapSqlParameterSource("noteId", noteId.toString()),
                JdbcClinicalNotes::toVoid).stream().findFirst();
    }

    @Override
    public List<NoteVoid> voidsInEncounter(UUID encounterId) {
        return jdbc.query(SELECT_VOID + " JOIN clinical_ledger.notes n ON n.id = v.note_id WHERE n.encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId.toString()), JdbcClinicalNotes::toVoid);
    }

    static SignedNote toNote(ResultSet row, int index) throws SQLException {
        return new SignedNote(Rows.uuid(row, "id"), Rows.uuid(row, "encounter_id"), Rows.clinician(row, "author_uuid", "author_role"),
                row.getString("author_email"), NoteContentColumn.read(row.getString("content")), Rows.instant(row, "occurred_at"), Rows.instant(row, "recorded_at"),
                row.getBoolean("extemporaneous"));
    }

    static NoteVoid toVoid(ResultSet row, int index) throws SQLException {
        return new NoteVoid(Rows.uuid(row, "note_id"), row.getString("reason"), Rows.clinician(row, "voided_by", "voided_by_role"),
                Rows.instant(row, "voided_at"));
    }
}
