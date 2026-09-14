package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcNoteDrafts implements NoteDrafts {

    private static final String SELECT = """
            SELECT id, encounter_id, type, content_key_id, content_ciphertext, author_uuid, author_role, occurred_at, version, created_at, updated_at
            FROM clinical_workspace.note_drafts""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;

    JdbcNoteDrafts(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    @Override
    public void add(NoteDraft draft) {
        jdbc.update("""
                INSERT INTO clinical_workspace.note_drafts
                    (id, encounter_id, type, content_key_id, content_ciphertext, author_uuid, author_role, occurred_at, version, created_at,
                     updated_at)
                VALUES (:id, :encounterId, :type, :contentKeyId, :contentCiphertext, :authorUuid, :authorRole, :occurredAt, :version,
                        :createdAt, :updatedAt)""",
                parametersOf(draft));
    }

    @Override
    public Optional<NoteDraft> find(UUID id) {
        return jdbc.query(SELECT + " WHERE id = :id", new MapSqlParameterSource("id", id.toString()), this::toDraft)
                .stream().findFirst();
    }

    @Override
    public Optional<NoteDraft> lock(UUID id) {
        return jdbc.query(SELECT + " WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id.toString()), this::toDraft)
                .stream().findFirst();
    }

    @Override
    public boolean replace(NoteDraft revised, long expectedVersion) {
        return jdbc.update("""
                UPDATE clinical_workspace.note_drafts
                SET content_key_id = :contentKeyId, content_ciphertext = :contentCiphertext, occurred_at = :occurredAt,
                    version = :version, updated_at = :updatedAt
                WHERE id = :id AND version = :expectedVersion""",
                parametersOf(revised).addValue("expectedVersion", expectedVersion)) == 1;
    }

    @Override
    public void remove(UUID id) {
        jdbc.update("DELETE FROM clinical_workspace.note_drafts WHERE id = :id", new MapSqlParameterSource("id", id.toString()));
    }

    @Override
    public List<NoteDraft> writtenBy(UUID authorUuid) {
        return jdbc.query(SELECT + " WHERE author_uuid = :authorUuid ORDER BY updated_at DESC, id",
                new MapSqlParameterSource("authorUuid", authorUuid.toString()), this::toDraft);
    }

    private MapSqlParameterSource parametersOf(NoteDraft draft) {
        UUID patientUuid = jdbc.queryForObject("SELECT patient_uuid FROM clinical_ledger.encounters WHERE id = :encounterId",
                new MapSqlParameterSource("encounterId", draft.encounterId().toString()), (row, index) -> Rows.uuid(row, "patient_uuid"));
        EncryptedField content = encryption.encrypt(patientUuid, Purpose.DRAFT_CONTENT, draft.id(),
                NoteContentColumn.write(draft.content()));
        return new MapSqlParameterSource()
                .addValue("id", draft.id().toString())
                .addValue("encounterId", draft.encounterId().toString())
                .addValue("type", draft.type().name())
                .addValue("contentKeyId", content.dataKeyId().toString())
                .addValue("contentCiphertext", content.ciphertext())
                .addValue("authorUuid", draft.author().uuid().toString())
                .addValue("authorRole", draft.author().role().name())
                .addValue("occurredAt", Rows.timestamp(draft.occurredAt()))
                .addValue("version", draft.version())
                .addValue("createdAt", Rows.timestamp(draft.createdAt()))
                .addValue("updatedAt", Rows.timestamp(draft.updatedAt()));
    }

    private NoteDraft toDraft(ResultSet row, int index) throws SQLException {
        UUID id = Rows.uuid(row, "id");
        byte[] content = encryption.decrypt(new EncryptedField(Rows.uuid(row, "content_key_id"), row.getBytes("content_ciphertext")),
                Purpose.DRAFT_CONTENT, id);
        return new NoteDraft(id, Rows.uuid(row, "encounter_id"), Rows.clinician(row, "author_uuid", "author_role"),
                NoteContentColumn.read(content, NoteType.valueOf(row.getString("type")), id), Rows.instant(row, "occurred_at"), row.getLong("version"),
                Rows.instant(row, "created_at"), Rows.instant(row, "updated_at"));
    }
}
