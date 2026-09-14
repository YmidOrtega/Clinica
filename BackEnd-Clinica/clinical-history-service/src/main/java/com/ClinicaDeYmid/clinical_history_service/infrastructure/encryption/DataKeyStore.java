package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

class DataKeyStore {

    record Wrapping(UUID dataKeyId, UUID patientUuid, String masterKeyId, byte[] wrappedKey) {
    }

    private static final String SELECT_WRAPPINGS = """
            SELECT k.id, k.patient_uuid, w.master_key_id, w.wrapped_key
            FROM clinical_keys.data_keys k
            JOIN clinical_keys.data_key_wrappings w ON w.data_key_id = k.id""";

    private final NamedParameterJdbcTemplate jdbc;

    DataKeyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<UUID> dataKeyOf(UUID patientUuid) {
        return jdbc.queryForList("SELECT id FROM clinical_keys.data_keys WHERE patient_uuid = :patientUuid FOR SHARE",
                new MapSqlParameterSource("patientUuid", patientUuid.toString()), String.class).stream().map(UUID::fromString).findFirst();
    }

    boolean insertDataKey(UUID dataKeyId, UUID patientUuid, Instant createdAt) {
        return jdbc.update("INSERT IGNORE INTO clinical_keys.data_keys (id, patient_uuid, created_at) VALUES (:id, :patientUuid, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", dataKeyId.toString())
                        .addValue("patientUuid", patientUuid.toString())
                        .addValue("createdAt", Timestamp.from(createdAt))) == 1;
    }

    void insertWrapping(UUID dataKeyId, String masterKeyId, byte[] wrappedKey, Instant createdAt) {
        jdbc.update("""
                INSERT INTO clinical_keys.data_key_wrappings (data_key_id, master_key_id, wrapped_key, created_at)
                VALUES (:dataKeyId, :masterKeyId, :wrappedKey, :createdAt)""",
                new MapSqlParameterSource()
                        .addValue("dataKeyId", dataKeyId.toString())
                        .addValue("masterKeyId", masterKeyId)
                        .addValue("wrappedKey", wrappedKey)
                        .addValue("createdAt", Timestamp.from(createdAt)));
    }

    List<Wrapping> wrappingsOf(UUID dataKeyId) {
        return jdbc.query(SELECT_WRAPPINGS + " WHERE k.id = :dataKeyId", new MapSqlParameterSource("dataKeyId", dataKeyId.toString()),
                (row, index) -> new Wrapping(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("patient_uuid")),
                        row.getString("master_key_id"), row.getBytes("wrapped_key")));
    }

    List<UUID> dataKeysNotWrappedBy(String masterKeyId, int limit) {
        return jdbc.queryForList("""
                SELECT k.id FROM clinical_keys.data_keys k
                WHERE NOT EXISTS (SELECT 1 FROM clinical_keys.data_key_wrappings w WHERE w.data_key_id = k.id AND w.master_key_id = :masterKeyId)
                ORDER BY k.id LIMIT :limit""",
                new MapSqlParameterSource().addValue("masterKeyId", masterKeyId).addValue("limit", limit), String.class)
                .stream().map(UUID::fromString).toList();
    }

    long countDataKeys() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM clinical_keys.data_keys", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    long countNotWrappedBy(String masterKeyId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM clinical_keys.data_keys k
                WHERE NOT EXISTS (SELECT 1 FROM clinical_keys.data_key_wrappings w WHERE w.data_key_id = k.id AND w.master_key_id = :masterKeyId)""",
                new MapSqlParameterSource("masterKeyId", masterKeyId), Long.class);
        return count == null ? 0 : count;
    }

    Map<String, Long> wrappingsPerMasterKey() {
        return jdbc.query("SELECT master_key_id, COUNT(*) AS wrapped FROM clinical_keys.data_key_wrappings GROUP BY master_key_id",
                        (row, index) -> Map.entry(row.getString("master_key_id"), row.getLong("wrapped"))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
