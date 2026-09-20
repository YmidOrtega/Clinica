package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.clinical_history_service.domain.practitioner.PractitionerReferences;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
class JdbcPractitionerReferences implements PractitionerReferences {

    private static final String COLUMNS =
            "user_uuid, practitioner_uuid, source_version, full_name, registration_number, specialty, status, account_linked";

    private static final String INSERT = "INSERT INTO practitioner_references (" + COLUMNS + ", updated_at) VALUES "
            + "(:userUuid, :practitionerUuid, :sourceVersion, :fullName, :registrationNumber, :specialty, :status, "
            + ":accountLinked, :updatedAt)";

    private static final String UPDATE = """
            UPDATE practitioner_references
               SET practitioner_uuid = :practitionerUuid, source_version = :sourceVersion, full_name = :fullName,
                   registration_number = :registrationNumber, specialty = :specialty, status = :status,
                   account_linked = :accountLinked, updated_at = :updatedAt
             WHERE user_uuid = :userUuid AND source_version < :sourceVersion""";

    private static final RowMapper<PractitionerReference> ROW_MAPPER = JdbcPractitionerReferences::toReference;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcPractitionerReferences(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<PractitionerReference> find(UUID userUuid) {
        return jdbc.query("SELECT " + COLUMNS + " FROM practitioner_references WHERE user_uuid = :userUuid",
                new MapSqlParameterSource("userUuid", userUuid.toString()), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public Map<UUID, PractitionerReference> findAll(Collection<UUID> userUuids) {
        if (userUuids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = userUuids.stream().map(UUID::toString).toList();
        return jdbc.query("SELECT " + COLUMNS + " FROM practitioner_references WHERE user_uuid IN (:userUuids)",
                        new MapSqlParameterSource("userUuids", keys), ROW_MAPPER).stream()
                .collect(Collectors.toMap(PractitionerReference::userUuid, Function.identity()));
    }

    @Override
    public boolean saveIfNewer(PractitionerReference reference) {
        MapSqlParameterSource parameters = parameters(reference);
        if (jdbc.update(UPDATE, parameters) > 0) {
            return true;
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM practitioner_references WHERE user_uuid = :userUuid",
                new MapSqlParameterSource("userUuid", reference.userUuid().toString()), Integer.class) > 0) {
            return false;
        }
        try {
            jdbc.update(INSERT, parameters);
            return true;
        } catch (DuplicateKeyException alreadyThere) {
            return false;
        }
    }

    @Override
    public void accountUnlinkedFrom(UUID practitionerUuid) {
        jdbc.update("""
                        UPDATE practitioner_references SET account_linked = FALSE, updated_at = :updatedAt
                         WHERE practitioner_uuid = :practitionerUuid""",
                new MapSqlParameterSource("practitionerUuid", practitionerUuid.toString())
                        .addValue("updatedAt", Timestamp.from(Instant.now(clock))));
    }

    private MapSqlParameterSource parameters(PractitionerReference reference) {
        return new MapSqlParameterSource()
                .addValue("userUuid", reference.userUuid().toString())
                .addValue("practitionerUuid", reference.practitionerUuid().toString())
                .addValue("sourceVersion", reference.sourceVersion())
                .addValue("fullName", reference.fullName())
                .addValue("registrationNumber", reference.registrationNumber())
                .addValue("specialty", reference.specialty())
                .addValue("status", reference.status())
                .addValue("accountLinked", reference.accountLinked())
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));
    }

    private static PractitionerReference toReference(ResultSet row, int rowNumber) throws SQLException {
        return new PractitionerReference(UUID.fromString(row.getString("user_uuid")),
                UUID.fromString(row.getString("practitioner_uuid")), row.getLong("source_version"),
                row.getString("full_name"), row.getString("registration_number"), row.getString("specialty"),
                row.getString("status"), row.getBoolean("account_linked"));
    }
}
