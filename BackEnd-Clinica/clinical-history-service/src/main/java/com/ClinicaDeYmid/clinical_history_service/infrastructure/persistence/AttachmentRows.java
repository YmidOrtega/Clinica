package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.AttachmentMediaType;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class AttachmentRows {

    static final String COLUMNS = "id, media_type, size_bytes, sha256, name_key_id, name_ciphertext";

    private AttachmentRows() {
    }

    static MapSqlParameterSource parameters(Attachment attachment, UUID patientUuid, ContentEncryption encryption) {
        EncryptedField name = encryption.encrypt(patientUuid, Purpose.ATTACHMENT_NAME, attachment.id(),
                attachment.fileName().getBytes(StandardCharsets.UTF_8));
        return new MapSqlParameterSource()
                .addValue("id", attachment.id().toString())
                .addValue("mediaType", attachment.mediaType().name())
                .addValue("sizeBytes", attachment.size())
                .addValue("sha256", attachment.sha256())
                .addValue("nameKeyId", name.dataKeyId().toString())
                .addValue("nameCiphertext", name.ciphertext());
    }

    static Attachment attachment(ResultSet row, ContentEncryption encryption) throws SQLException {
        UUID id = Rows.uuid(row, "id");
        byte[] name = encryption.decrypt(new EncryptedField(Rows.uuid(row, "name_key_id"), row.getBytes("name_ciphertext")),
                Purpose.ATTACHMENT_NAME, id);
        return new Attachment(id, new String(name, StandardCharsets.UTF_8), AttachmentMediaType.valueOf(row.getString("media_type")),
                row.getLong("size_bytes"), row.getString("sha256"));
    }
}
