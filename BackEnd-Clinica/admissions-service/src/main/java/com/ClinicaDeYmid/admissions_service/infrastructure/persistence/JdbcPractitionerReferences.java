package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReferences;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPractitionerReferences implements PractitionerReferences {

    private static final String TABLE = "admissions.practitioner_references";

    private static final String COLUMNS =
            "practitioner_uuid, source_version, full_name, registration_number, specialty, status, user_uuid";

    private static final String INSERT = "INSERT INTO " + TABLE + " (" + COLUMNS + ", updated_at) VALUES "
            + "(:practitionerUuid, :sourceVersion, :fullName, :registrationNumber, :specialty, :status, :userUuid, "
            + ":updatedAt)";

    private static final String UPDATE = """
            UPDATE admissions.practitioner_references SET source_version = :sourceVersion, full_name = :fullName,
                registration_number = :registrationNumber, specialty = :specialty, status = :status,
                user_uuid = :userUuid, updated_at = :updatedAt
            WHERE practitioner_uuid = :practitionerUuid AND source_version < :sourceVersion""";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final Clock clock;

    JdbcPractitionerReferences(NamedParameterJdbcTemplate jdbc, TransactionOperations transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Optional<PractitionerReference> find(UUID practitionerUuid) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE practitioner_uuid = :practitionerUuid",
                new MapSqlParameterSource("practitionerUuid", practitionerUuid), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public boolean saveIfNewer(PractitionerReference reference) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("practitionerUuid", reference.practitionerUuid())
                .addValue("sourceVersion", reference.sourceVersion())
                .addValue("fullName", reference.fullName())
                .addValue("registrationNumber", reference.registrationNumber())
                .addValue("specialty", reference.specialty())
                .addValue("status", reference.status())
                .addValue("userUuid", reference.userUuid())
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));
        Boolean saved = transactions.execute(status -> {
            if (jdbc.update(UPDATE, parameters) == 1) {
                return true;
            }
            try {
                return jdbc.update(INSERT, parameters) == 1;
            } catch (DuplicateKeyException alreadyPresent) {
                return false;
            }
        });
        return Boolean.TRUE.equals(saved);
    }

    private static final RowMapper<PractitionerReference> ROW_MAPPER = (row, number) -> new PractitionerReference(
            row.getObject("practitioner_uuid", UUID.class), row.getLong("source_version"), row.getString("full_name"),
            row.getString("registration_number"), row.getString("specialty"), row.getString("status"),
            row.getObject("user_uuid", UUID.class));
}
