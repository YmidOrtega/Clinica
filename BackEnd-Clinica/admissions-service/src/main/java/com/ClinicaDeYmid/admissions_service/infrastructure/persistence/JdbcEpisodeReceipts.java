package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipt;
import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipts;
import com.ClinicaDeYmid.commons.documents.DocumentSeal;
import com.ClinicaDeYmid.commons.documents.SealedDocument;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcEpisodeReceipts implements EpisodeReceipts {

    private static final RowMapper<EpisodeReceipt> ROW_MAPPER = (ResultSet row, int index) -> {
        SealedDocument document = new SealedDocument(UUID.fromString(row.getString("id")), EpisodeReceipt.PURPOSE,
                UUID.fromString(row.getString("admission_uuid")), UUID.fromString(row.getString("issued_by")),
                row.getString("issued_by_role"), row.getTimestamp("issued_at").toInstant(),
                row.getString("document_sha256"),
                new DocumentSeal(row.getString("key_id"), row.getString("seal")));
        return new EpisodeReceipt(document, row.getString("admission_number"));
    };

    private static final String SELECT = """
            SELECT r.id, r.admission_number, r.issued_by, r.issued_by_role, r.issued_at, r.document_sha256,
                   r.key_id, r.seal, a.uuid AS admission_uuid
            FROM admissions.episode_receipts r
            JOIN admissions.admissions a ON a.id = r.admission_id""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcEpisodeReceipts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(EpisodeReceipt receipt) {
        jdbc.update("""
                INSERT INTO admissions.episode_receipts (id, admission_id, admission_number, issued_by,
                    issued_by_role, issued_at, document_sha256, key_id, seal)
                SELECT :id, a.id, :number, :issuedBy, :issuedByRole, :issuedAt, :sha256, :keyId, :seal
                FROM admissions.admissions a WHERE a.uuid = :admissionUuid""",
                new MapSqlParameterSource()
                        .addValue("id", receipt.id())
                        .addValue("number", receipt.admissionNumber())
                        .addValue("issuedBy", receipt.issuedBy())
                        .addValue("issuedByRole", receipt.issuedByRole())
                        .addValue("issuedAt", Timestamp.from(receipt.issuedAt()))
                        .addValue("sha256", receipt.sha256())
                        .addValue("keyId", receipt.keyId())
                        .addValue("seal", receipt.seal())
                        .addValue("admissionUuid", receipt.admissionUuid()));
    }

    @Override
    public Optional<EpisodeReceipt> find(UUID id) {
        return jdbc.query(SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Optional<EpisodeReceipt> findByNumberAndFingerprint(String admissionNumber, String sha256) {
        return jdbc.query(SELECT + " WHERE r.admission_number = :number AND r.document_sha256 = :sha256",
                new MapSqlParameterSource("number", admissionNumber).addValue("sha256", sha256),
                ROW_MAPPER).stream().findFirst();
    }
}
