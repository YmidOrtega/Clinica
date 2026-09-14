package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLinks;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcChainLinks implements ChainLinks {

    private static final String SELECT = """
            SELECT patient_uuid, sequence, entry_type, entry_id, format_version, payload_hash, previous_hash, entry_hash, key_id, seal,
                   sealed_at
            FROM clinical_ledger.chain_links""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcChainLinks(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChainLink> lockHead(UUID patientUuid) {
        MapSqlParameterSource patient = new MapSqlParameterSource("patientUuid", patientUuid.toString());
        List<String> locked = jdbc.queryForList("SELECT uuid FROM patient_references WHERE uuid = :patientUuid FOR UPDATE", patient,
                String.class);
        if (locked.isEmpty()) {
            throw new IllegalStateException("Cannot seal the record of a patient missing from the local copy: " + patientUuid);
        }
        return jdbc.query(SELECT + " WHERE patient_uuid = :patientUuid ORDER BY sequence DESC LIMIT 1", patient, JdbcChainLinks::toLink)
                .stream().findFirst();
    }

    @Override
    public void append(ChainLink link) {
        jdbc.update("""
                INSERT INTO clinical_ledger.chain_links (patient_uuid, sequence, entry_type, entry_id, format_version, payload_hash,
                    previous_hash, entry_hash, key_id, seal, sealed_at)
                VALUES (:patientUuid, :sequence, :entryType, :entryId, :formatVersion, :payloadHash, :previousHash, :entryHash, :keyId,
                    :seal, :sealedAt)""",
                new MapSqlParameterSource()
                        .addValue("patientUuid", link.patientUuid().toString())
                        .addValue("sequence", link.sequence())
                        .addValue("entryType", link.entryType().name())
                        .addValue("entryId", link.entryId().toString())
                        .addValue("formatVersion", link.formatVersion())
                        .addValue("payloadHash", link.payloadHash())
                        .addValue("previousHash", link.previousHash())
                        .addValue("entryHash", link.entryHash())
                        .addValue("keyId", link.keyId())
                        .addValue("seal", link.seal())
                        .addValue("sealedAt", Rows.timestamp(link.sealedAt())));
    }

    @Override
    public List<ChainLink> chainOf(UUID patientUuid) {
        return jdbc.query(SELECT + " WHERE patient_uuid = :patientUuid ORDER BY sequence",
                new MapSqlParameterSource("patientUuid", patientUuid.toString()), JdbcChainLinks::toLink);
    }

    @Override
    public Optional<ChainLink> linkOf(LedgerEntry.Key entry) {
        return jdbc.query(SELECT + " WHERE entry_type = :entryType AND entry_id = :entryId",
                new MapSqlParameterSource()
                        .addValue("entryType", entry.type().name())
                        .addValue("entryId", entry.entryId().toString()),
                JdbcChainLinks::toLink).stream().findFirst();
    }

    private static ChainLink toLink(ResultSet row, int index) throws SQLException {
        return new ChainLink(Rows.uuid(row, "patient_uuid"), row.getLong("sequence"), EntryType.valueOf(row.getString("entry_type")),
                Rows.uuid(row, "entry_id"), row.getInt("format_version"), row.getString("payload_hash"), row.getString("previous_hash"),
                row.getString("entry_hash"), row.getString("key_id"), row.getString("seal"), Rows.instant(row, "sealed_at"));
    }
}
