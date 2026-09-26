package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.terminology.RipsCatalog;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRipsCatalog implements RipsCatalog {

    private static final RowMapper<ReferenceCode> CODE = (row, index) -> new ReferenceCode(
            Table.valueOf(row.getString("reference_table")), row.getString("code"), row.getString("name"),
            row.getString("service_group"));

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRipsCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReferenceCode> find(Table table, String code) {
        return jdbc.query("""
                SELECT reference_table, code, name, service_group FROM rips_reference_codes
                WHERE reference_table = :table AND code = :code""",
                new MapSqlParameterSource("table", table.name()).addValue("code", code), CODE).stream().findFirst();
    }

    @Override
    public List<ReferenceCode> list(Table table) {
        return jdbc.query("""
                SELECT reference_table, code, name, service_group FROM rips_reference_codes
                WHERE reference_table = :table ORDER BY code""", new MapSqlParameterSource("table", table.name()), CODE);
    }

    @Override
    public boolean habilitated(String serviceCode, String modality) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM habilitated_services
                WHERE service_code = :service AND modality = :modality AND active""",
                new MapSqlParameterSource("service", serviceCode).addValue("modality", modality), Boolean.class));
    }

    @Override
    public List<HabilitatedService> habilitatedServices() {
        return jdbc.query("""
                SELECT h.service_code, r.name, r.service_group, h.modality, h.active, h.changed_at, h.changed_by
                FROM habilitated_services h
                JOIN rips_reference_codes r ON r.reference_table = 'SERVICE' AND r.code = h.service_code
                ORDER BY h.service_code, h.modality""", (row, index) -> new HabilitatedService(
                row.getString("service_code"), row.getString("name"), row.getString("service_group"),
                row.getString("modality"), row.getBoolean("active"), row.getTimestamp("changed_at").toInstant(),
                UUID.fromString(row.getString("changed_by"))));
    }

    @Override
    public void habilitate(String serviceCode, String modality, boolean active, UUID changedBy, Instant at) {
        jdbc.update("""
                INSERT INTO habilitated_services (service_code, modality, active, changed_at, changed_by)
                VALUES (:service, :modality, :active, :at, :by)
                ON DUPLICATE KEY UPDATE active = :active, changed_at = :at, changed_by = :by""",
                new MapSqlParameterSource("service", serviceCode).addValue("modality", modality)
                        .addValue("active", active).addValue("at", Timestamp.from(at)).addValue("by", changedBy.toString()));
    }
}
