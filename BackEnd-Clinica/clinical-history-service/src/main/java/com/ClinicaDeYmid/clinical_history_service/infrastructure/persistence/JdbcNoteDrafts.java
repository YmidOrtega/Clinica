package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
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
            SELECT id, encounter_id, content, author_uuid, author_role, occurred_at, version, created_at, updated_at
            FROM clinical_workspace.note_drafts""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcNoteDrafts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(NoteDraft draft) {
        jdbc.update("""
                INSERT INTO clinical_workspace.note_drafts
                    (id, encounter_id, type, content, author_uuid, author_role, occurred_at, version, created_at, updated_at)
                VALUES (:id, :encounterId, :type, :content, :authorUuid, :authorRole, :occurredAt, :version, :createdAt, :updatedAt)""",
                parametersOf(draft));
    }

    @Override
    public Optional<NoteDraft> find(UUID id) {
        return jdbc.query(SELECT + " WHERE id = :id", new MapSqlParameterSource("id", id.toString()), JdbcNoteDrafts::toDraft)
                .stream().findFirst();
    }

    @Override
    public Optional<NoteDraft> lock(UUID id) {
        return jdbc.query(SELECT + " WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id.toString()), JdbcNoteDrafts::toDraft)
                .stream().findFirst();
    }

    @Override
    public boolean replace(NoteDraft revised, long expectedVersion) {
        return jdbc.update("""
                UPDATE clinical_workspace.note_drafts
                SET content = :content, occurred_at = :occurredAt, version = :version, updated_at = :updatedAt
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
                new MapSqlParameterSource("authorUuid", authorUuid.toString()), JdbcNoteDrafts::toDraft);
    }

    private static MapSqlParameterSource parametersOf(NoteDraft draft) {
        return new MapSqlParameterSource()
                .addValue("id", draft.id().toString())
                .addValue("encounterId", draft.encounterId().toString())
                .addValue("type", draft.type().name())
                .addValue("content", NoteContentColumn.write(draft.content()))
                .addValue("authorUuid", draft.author().uuid().toString())
                .addValue("authorRole", draft.author().role().name())
                .addValue("occurredAt", Rows.timestamp(draft.occurredAt()))
                .addValue("version", draft.version())
                .addValue("createdAt", Rows.timestamp(draft.createdAt()))
                .addValue("updatedAt", Rows.timestamp(draft.updatedAt()));
    }

    private static NoteDraft toDraft(ResultSet row, int index) throws SQLException {
        return new NoteDraft(Rows.uuid(row, "id"), Rows.uuid(row, "encounter_id"), Rows.clinician(row, "author_uuid", "author_role"),
                NoteContentColumn.read(row.getString("content")), Rows.instant(row, "occurred_at"), row.getLong("version"),
                Rows.instant(row, "created_at"), Rows.instant(row, "updated_at"));
    }
}
