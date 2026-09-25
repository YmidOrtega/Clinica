package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.NumberingCounters;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcNumberingCounters implements NumberingCounters {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcNumberingCounters(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void open(NumberingResolution resolution) {
        jdbc.update("""
                INSERT INTO numbering_counters (resolution_id, next_number)
                SELECT id, range_from FROM numbering_resolutions WHERE uuid = :uuid
                """, new MapSqlParameterSource("uuid", resolution.uuid().toString()));
    }

    @Override
    public long lockNext(UUID resolutionUuid) {
        return jdbc.queryForObject("""
                SELECT c.next_number FROM numbering_counters c
                JOIN numbering_resolutions r ON r.id = c.resolution_id
                WHERE r.uuid = :uuid
                FOR UPDATE
                """, new MapSqlParameterSource("uuid", resolutionUuid.toString()), Long.class);
    }

    @Override
    public void advance(UUID resolutionUuid) {
        jdbc.update("""
                UPDATE numbering_counters c
                JOIN numbering_resolutions r ON r.id = c.resolution_id
                SET c.next_number = c.next_number + 1
                WHERE r.uuid = :uuid
                """, new MapSqlParameterSource("uuid", resolutionUuid.toString()));
    }

    @Override
    public Map<UUID, Long> nextNumbers(Collection<UUID> resolutionUuids) {
        Map<UUID, Long> next = new HashMap<>();
        if (resolutionUuids.isEmpty()) {
            return next;
        }
        jdbc.query("""
                SELECT r.uuid, c.next_number FROM numbering_counters c
                JOIN numbering_resolutions r ON r.id = c.resolution_id
                WHERE r.uuid IN (:uuids)
                """, new MapSqlParameterSource("uuids", resolutionUuids.stream().map(UUID::toString).toList()),
                row -> {
                    next.put(UUID.fromString(row.getString("uuid")), row.getLong("next_number"));
                });
        return next;
    }
}
