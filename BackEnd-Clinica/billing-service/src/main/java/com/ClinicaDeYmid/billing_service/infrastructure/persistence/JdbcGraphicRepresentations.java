package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentation;
import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentations;
import com.ClinicaDeYmid.commons.documents.DocumentSeal;
import com.ClinicaDeYmid.commons.documents.SealedDocument;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcGraphicRepresentations implements GraphicRepresentations {

    private static final RowMapper<GraphicRepresentation> ROW_MAPPER = (ResultSet row, int index) ->
            new GraphicRepresentation(new SealedDocument(UUID.fromString(row.getString("id")),
                    GraphicRepresentation.PURPOSE, UUID.fromString(row.getString("document_uuid")),
                    UUID.fromString(row.getString("issued_by")), row.getString("issued_by_role"),
                    row.getTimestamp("issued_at").toInstant(), row.getString("document_sha256"),
                    new DocumentSeal(row.getString("key_id"), row.getString("seal"))),
                    row.getString("document_number"));

    private static final String SELECT = """
            SELECT g.id, g.document_number, g.issued_by, g.issued_by_role, g.issued_at, g.document_sha256,
                   g.key_id, g.seal, e.uuid AS document_uuid
            FROM graphic_representations g
            JOIN electronic_documents e ON e.id = g.electronic_document_id""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcGraphicRepresentations(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(GraphicRepresentation representation) {
        int inserted = jdbc.update("""
                INSERT INTO graphic_representations (id, electronic_document_id, document_number, issued_by,
                    issued_by_role, issued_at, document_sha256, key_id, seal)
                SELECT :id, e.id, :number, :issuedBy, :issuedByRole, :issuedAt, :sha256, :keyId, :seal
                FROM electronic_documents e WHERE e.uuid = :documentUuid""",
                new MapSqlParameterSource()
                        .addValue("id", representation.id().toString())
                        .addValue("number", representation.documentNumber())
                        .addValue("issuedBy", representation.issuedBy().toString())
                        .addValue("issuedByRole", representation.issuedByRole())
                        .addValue("issuedAt", Timestamp.from(representation.issuedAt()))
                        .addValue("sha256", representation.sha256())
                        .addValue("keyId", representation.keyId())
                        .addValue("seal", representation.seal())
                        .addValue("documentUuid", representation.electronicDocumentUuid().toString()));
        if (inserted != 1) {
            throw new IllegalStateException("Unknown electronic document " + representation.electronicDocumentUuid());
        }
    }

    @Override
    public Optional<GraphicRepresentation> find(UUID id) {
        return jdbc.query(SELECT + " WHERE g.id = :id", new MapSqlParameterSource("id", id.toString()), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Optional<GraphicRepresentation> findByNumberAndFingerprint(String documentNumber, String sha256) {
        return jdbc.query(SELECT + " WHERE g.document_number = :number AND g.document_sha256 = :sha256",
                new MapSqlParameterSource("number", documentNumber).addValue("sha256", sha256), ROW_MAPPER)
                .stream().findFirst();
    }
}
