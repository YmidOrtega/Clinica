package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.DraftAttachments;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDraftAttachments implements DraftAttachments {

    private static final String SELECT = "SELECT draft_id, uploaded_at, " + AttachmentRows.COLUMNS + " FROM clinical_workspace.draft_attachments";

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;

    JdbcDraftAttachments(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    @Override
    public void add(DraftAttachment attachment, UUID patientUuid) {
        jdbc.update("""
                INSERT INTO clinical_workspace.draft_attachments (id, draft_id, media_type, size_bytes, sha256, name_key_id, name_ciphertext,
                    uploaded_at)
                VALUES (:id, :draftId, :mediaType, :sizeBytes, :sha256, :nameKeyId, :nameCiphertext, :uploadedAt)""",
                AttachmentRows.parameters(attachment.attachment(), patientUuid, encryption)
                        .addValue("draftId", attachment.draftId().toString())
                        .addValue("uploadedAt", Rows.timestamp(attachment.uploadedAt())));
    }

    @Override
    public List<DraftAttachment> ofDraft(UUID draftId) {
        return jdbc.query(SELECT + " WHERE draft_id = :draftId ORDER BY uploaded_at, id", new MapSqlParameterSource("draftId", draftId.toString()),
                this::toAttachment);
    }

    @Override
    public Optional<DraftAttachment> find(UUID draftId, UUID attachmentId) {
        return jdbc.query(SELECT + " WHERE draft_id = :draftId AND id = :id",
                new MapSqlParameterSource().addValue("draftId", draftId.toString()).addValue("id", attachmentId.toString()),
                this::toAttachment).stream().findFirst();
    }

    @Override
    public void remove(UUID attachmentId) {
        jdbc.update("DELETE FROM clinical_workspace.draft_attachments WHERE id = :id", new MapSqlParameterSource("id", attachmentId.toString()));
    }


    private DraftAttachment toAttachment(ResultSet row, int index) throws SQLException {
        return new DraftAttachment(Rows.uuid(row, "draft_id"), AttachmentRows.attachment(row, encryption), Rows.instant(row, "uploaded_at"));
    }
}
