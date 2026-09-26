package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DianFileCounters;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcDianFileCounters implements DianFileCounters {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcDianFileCounters(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(int year) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("year", year);
        jdbc.update("""
                INSERT INTO dian_file_counters (year, next_value) VALUES (:year, 1)
                ON DUPLICATE KEY UPDATE next_value = next_value + 1
                """, parameters);
        return jdbc.queryForObject("SELECT next_value FROM dian_file_counters WHERE year = :year", parameters, Long.class);
    }
}
