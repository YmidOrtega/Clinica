package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.application.Geography;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcGeography implements Geography {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcGeography(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> departmentOfMunicipality(String municipalityCode) {
        return jdbc.queryForList("SELECT department_code FROM geo_municipalities WHERE code = :code",
                new MapSqlParameterSource("code", municipalityCode), String.class).stream().findFirst();
    }

    @Override
    public Optional<String> countryCode(String alpha2) {
        return jdbc.queryForList("SELECT numeric_code FROM geo_countries WHERE alpha2 = :alpha2",
                new MapSqlParameterSource("alpha2", alpha2), String.class).stream().findFirst();
    }
}
