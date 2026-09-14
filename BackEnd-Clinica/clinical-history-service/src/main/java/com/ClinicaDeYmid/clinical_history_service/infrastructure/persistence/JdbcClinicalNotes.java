package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcClinicalNotes implements ClinicalNotes {

    static final String SELECT_NOTE = """
            SELECT id, encounter_id, type, restriction, content_key_id, content_ciphertext, author_uuid, author_role, author_email, occurred_at,
                   recorded_at, extemporaneous
            FROM clinical_ledger.notes""";

    static final String SELECT_VOID = """
            SELECT v.note_id, v.reason_key_id, v.reason_ciphertext, v.voided_by, v.voided_by_role, v.voided_at
            FROM clinical_ledger.note_voids v""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;
    private final JdbcPatientChart chart;

    JdbcClinicalNotes(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption, JdbcPatientChart chart) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.chart = chart;
    }

    @Override
    public void append(SignedNote note) {
        UUID patientUuid = jdbc.queryForObject("SELECT patient_uuid FROM clinical_ledger.encounters WHERE id = :encounterId",
                new MapSqlParameterSource("encounterId", note.encounterId().toString()), (row, index) -> Rows.uuid(row, "patient_uuid"));
        EncryptedField content = encryption.encrypt(patientUuid, Purpose.NOTE_CONTENT, note.id(), NoteContentColumn.write(note.content()));
        jdbc.update("""
                INSERT INTO clinical_ledger.notes
                    (id, encounter_id, type, restriction, content_key_id, content_ciphertext, amends_note_id, author_uuid, author_role, author_email,
                     occurred_at, recorded_at, extemporaneous)
                VALUES (:id, :encounterId, :type, :restriction, :contentKeyId, :contentCiphertext, :amendsNoteId, :authorUuid, :authorRole, :authorEmail,
                        :occurredAt, :recordedAt, :extemporaneous)""",
                new MapSqlParameterSource()
                        .addValue("id", note.id().toString())
                        .addValue("encounterId", note.encounterId().toString())
                        .addValue("type", note.type().name())
                        .addValue("restriction", note.isRestricted() ? note.restriction().name() : null)
                        .addValue("contentKeyId", content.dataKeyId().toString())
                        .addValue("contentCiphertext", content.ciphertext())
                        .addValue("amendsNoteId", note.amends().map(UUID::toString).orElse(null))
                        .addValue("authorUuid", note.author().uuid().toString())
                        .addValue("authorRole", note.author().role().name())
                        .addValue("authorEmail", note.signerEmail())
                        .addValue("occurredAt", Rows.timestamp(note.occurredAt()))
                        .addValue("recordedAt", Rows.timestamp(note.recordedAt()))
                        .addValue("extemporaneous", note.extemporaneous()));
        chart.append(patientUuid, note);
    }

    @Override
    public Optional<SignedNote> find(UUID id) {
        return withUpdates(jdbc.query(SELECT_NOTE + " WHERE id = :id", new MapSqlParameterSource("id", id.toString()), this::toNote))
                .stream().findFirst();
    }

    @Override
    public List<SignedNote> ofEncounter(UUID encounterId) {
        return withUpdates(jdbc.query(SELECT_NOTE + " WHERE encounter_id = :encounterId ORDER BY recorded_at, id",
                new MapSqlParameterSource("encounterId", encounterId.toString()), this::toNote));
    }

    @Override
    public boolean addVoid(NoteVoid noteVoid) {
        UUID patientUuid = jdbc.queryForObject("""
                SELECT e.patient_uuid FROM clinical_ledger.notes n JOIN clinical_ledger.encounters e ON e.id = n.encounter_id
                WHERE n.id = :noteId""", new MapSqlParameterSource("noteId", noteVoid.noteId().toString()),
                (row, index) -> Rows.uuid(row, "patient_uuid"));
        EncryptedField reason = encryption.encrypt(patientUuid, Purpose.VOID_REASON, noteVoid.noteId(),
                noteVoid.reason().getBytes(StandardCharsets.UTF_8));
        try {
            return jdbc.update("""
                    INSERT INTO clinical_ledger.note_voids (note_id, reason_key_id, reason_ciphertext, voided_by, voided_by_role, voided_at)
                    VALUES (:noteId, :reasonKeyId, :reasonCiphertext, :voidedBy, :voidedByRole, :voidedAt)""",
                    new MapSqlParameterSource()
                            .addValue("noteId", noteVoid.noteId().toString())
                            .addValue("reasonKeyId", reason.dataKeyId().toString())
                            .addValue("reasonCiphertext", reason.ciphertext())
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
                this::toVoid).stream().findFirst();
    }

    @Override
    public List<NoteVoid> voidsInEncounter(UUID encounterId) {
        return jdbc.query(SELECT_VOID + " JOIN clinical_ledger.notes n ON n.id = v.note_id WHERE n.encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId.toString()), this::toVoid);
    }

    List<SignedNote> withUpdates(List<SignedNote> loaded) {
        Map<UUID, List<AppliedUpdate>> updates = chart.updatesOf(loaded.stream().map(SignedNote::id).toList());
        return loaded.stream().map(note -> !updates.containsKey(note.id()) ? note : new SignedNote(note.id(), note.encounterId(), note.author(),
                note.signerEmail(), note.content(), note.restriction(), updates.get(note.id()), note.occurredAt(), note.recordedAt(),
                note.extemporaneous())).toList();
    }

    SignedNote toNote(ResultSet row, int index) throws SQLException {
        UUID id = Rows.uuid(row, "id");
        NoteType type = NoteType.valueOf(row.getString("type"));
        byte[] content = encryption.decrypt(new EncryptedField(Rows.uuid(row, "content_key_id"), row.getBytes("content_ciphertext")),
                Purpose.NOTE_CONTENT, id);
        return new SignedNote(id, Rows.uuid(row, "encounter_id"), Rows.clinician(row, "author_uuid", "author_role"),
                row.getString("author_email"), NoteContentColumn.read(content, type, id), Rows.restriction(row), List.of(),
                Rows.instant(row, "occurred_at"),
                Rows.instant(row, "recorded_at"), row.getBoolean("extemporaneous"));
    }

    NoteVoid toVoid(ResultSet row, int index) throws SQLException {
        UUID noteId = Rows.uuid(row, "note_id");
        byte[] reason = encryption.decrypt(new EncryptedField(Rows.uuid(row, "reason_key_id"), row.getBytes("reason_ciphertext")),
                Purpose.VOID_REASON, noteId);
        return new NoteVoid(noteId, new String(reason, StandardCharsets.UTF_8), Rows.clinician(row, "voided_by", "voided_by_role"),
                Rows.instant(row, "voided_at"));
    }
}
