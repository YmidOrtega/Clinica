package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.ObjectionCatalog;
import com.ClinicaDeYmid.billing_service.domain.ObjectionCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JdbcObjectionCatalog implements ObjectionCatalog {

    private static final String COLUMNS =
            "SELECT code, kind, concept, group_code, applicable, description FROM objection_codes";
    private static final RowMapper<ObjectionCode> ROW = (row, index) -> new ObjectionCode(row.getString("code"),
            ObjectionCode.Kind.valueOf(row.getString("kind")), row.getString("concept"), row.getString("group_code"),
            row.getBoolean("applicable"), row.getString("description"));

    private final JdbcTemplate jdbc;

    JdbcObjectionCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ObjectionCode> find(String code) {
        return jdbc.query(COLUMNS + " WHERE code = ?", ROW, code).stream().findFirst();
    }

    @Override
    public List<ObjectionCode> list(ObjectionCode.Kind kind) {
        return kind == null ? jdbc.query(COLUMNS + " ORDER BY code", ROW)
                : jdbc.query(COLUMNS + " WHERE kind = ? ORDER BY code", ROW, kind.name());
    }
}
