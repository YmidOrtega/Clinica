package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopies;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopy;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRecordCopies implements RecordCopies {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;

    JdbcRecordCopies(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    @Override
    public void add(RecordCopy copy) {
        EncryptedField reason = encryption.encrypt(copy.patientUuid(), Purpose.RECORD_COPY_REASON, copy.id(),
                copy.reason().getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO clinical_ledger.record_copies (id, patient_uuid, requested_by, requested_role, reason_key_id, reason_ciphertext,
                    period_from, period_to, entries, chain_verified, document_sha256, key_id, seal, generated_at)
                VALUES (:id, :patientUuid, :requestedBy, :requestedRole, :reasonKeyId, :reasonCiphertext, :periodFrom, :periodTo, :entries,
                    :chainVerified, :documentSha256, :keyId, :seal, :generatedAt)""",
                new MapSqlParameterSource()
                        .addValue("id", copy.id().toString())
                        .addValue("patientUuid", copy.patientUuid().toString())
                        .addValue("requestedBy", copy.requestedBy().toString())
                        .addValue("requestedRole", copy.requestedRole())
                        .addValue("reasonKeyId", reason.dataKeyId().toString())
                        .addValue("reasonCiphertext", reason.ciphertext())
                        .addValue("periodFrom", copy.periodFrom() == null ? null : Rows.timestamp(copy.periodFrom()))
                        .addValue("periodTo", copy.periodTo() == null ? null : Rows.timestamp(copy.periodTo()))
                        .addValue("entries", copy.entries())
                        .addValue("chainVerified", copy.chainVerified())
                        .addValue("documentSha256", copy.documentSha256())
                        .addValue("keyId", copy.keyId())
                        .addValue("seal", copy.seal())
                        .addValue("generatedAt", Rows.timestamp(copy.generatedAt())));
    }

    @Override
    public Optional<RecordCopy> find(UUID id) {
        return jdbc.query("SELECT * FROM clinical_ledger.record_copies WHERE id = :id", new MapSqlParameterSource("id", id.toString()),
                (row, index) -> {
                    UUID copyId = Rows.uuid(row, "id");
                    byte[] reason = encryption.decrypt(new EncryptedField(Rows.uuid(row, "reason_key_id"), row.getBytes("reason_ciphertext")),
                            Purpose.RECORD_COPY_REASON, copyId);
                    return new RecordCopy(copyId, Rows.uuid(row, "patient_uuid"), Rows.uuid(row, "requested_by"), row.getString("requested_role"),
                            new String(reason, StandardCharsets.UTF_8), Rows.instant(row, "period_from"), Rows.instant(row, "period_to"),
                            row.getInt("entries"), row.getBoolean("chain_verified"), row.getString("document_sha256"), row.getString("key_id"),
                            row.getString("seal"), Rows.instant(row, "generated_at"));
                }).stream().findFirst();
    }
}
