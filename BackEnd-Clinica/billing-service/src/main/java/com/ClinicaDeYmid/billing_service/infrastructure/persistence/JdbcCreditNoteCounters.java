package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.CreditNoteCounters;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcCreditNoteCounters implements CreditNoteCounters {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCreditNoteCounters(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String prefix) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("prefix", prefix);
        jdbc.update("""
                INSERT INTO credit_note_counters (prefix, next_value) VALUES (:prefix, 1)
                ON DUPLICATE KEY UPDATE next_value = next_value + 1
                """, parameters);
        return jdbc.queryForObject("SELECT next_value FROM credit_note_counters WHERE prefix = :prefix", parameters,
                Long.class);
    }
}
