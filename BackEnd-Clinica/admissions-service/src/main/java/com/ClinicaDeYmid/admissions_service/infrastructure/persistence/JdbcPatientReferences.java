package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPatientReferences implements PatientReferences {

    private static final String TABLE = "admissions.patient_references";

    private static final String COLUMNS = """
            uuid, kind, source_version, document_type, document_number, first_names, last_names, birth_date, code,
            estimated_birth_year, sex, status, date_of_death, health_regime, payer_uuid, identified_patient_uuid""";

    private static final String INSERT = "INSERT INTO " + TABLE + " (" + COLUMNS + ", updated_at) VALUES "
            + "(:uuid, :kind, :sourceVersion, :documentType, :documentNumber, :firstNames, :lastNames, :birthDate, "
            + ":code, :estimatedBirthYear, :sex, :status, :dateOfDeath, :healthRegime, :payerUuid, "
            + ":identifiedPatientUuid, :updatedAt)";

    private static final String UPDATE = """
            UPDATE admissions.patient_references SET kind = :kind, source_version = :sourceVersion,
                document_type = :documentType, document_number = :documentNumber, first_names = :firstNames,
                last_names = :lastNames, birth_date = :birthDate, code = :code,
                estimated_birth_year = :estimatedBirthYear, sex = :sex, status = :status,
                date_of_death = :dateOfDeath, health_regime = :healthRegime, payer_uuid = :payerUuid,
                identified_patient_uuid = :identifiedPatientUuid, updated_at = :updatedAt
            WHERE uuid = :uuid AND source_version < :sourceVersion""";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final Clock clock;

    JdbcPatientReferences(NamedParameterJdbcTemplate jdbc, TransactionOperations transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Optional<PatientReference> find(UUID uuid) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE uuid = :uuid",
                new MapSqlParameterSource("uuid", uuid), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public Optional<PatientReference> findByDocument(String documentType, String documentNumber) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE
                        + " WHERE document_type = :type AND document_number = :number",
                new MapSqlParameterSource("type", documentType).addValue("number", documentNumber),
                ROW_MAPPER).stream().findFirst();
    }

    @Override
    public boolean saveIfNewer(PatientReference reference) {
        MapSqlParameterSource parameters = parametersOf(reference);
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

    private MapSqlParameterSource parametersOf(PatientReference reference) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("uuid", reference.uuid())
                .addValue("sourceVersion", reference.version())
                .addValue("sex", reference.sex().name())
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)))
                .addValue("documentType", null)
                .addValue("documentNumber", null)
                .addValue("firstNames", null)
                .addValue("lastNames", null)
                .addValue("birthDate", null)
                .addValue("code", null)
                .addValue("estimatedBirthYear", null)
                .addValue("dateOfDeath", null)
                .addValue("healthRegime", null)
                .addValue("payerUuid", null)
                .addValue("identifiedPatientUuid", null);

        switch (reference) {
            case PatientReference.Registered registered -> parameters
                    .addValue("kind", "REGISTERED")
                    .addValue("documentType", registered.document().type())
                    .addValue("documentNumber", registered.document().number())
                    .addValue("firstNames", registered.firstNames())
                    .addValue("lastNames", registered.lastNames())
                    .addValue("birthDate", Date.valueOf(registered.birthDate()))
                    .addValue("status", registered.status().name())
                    .addValue("dateOfDeath", registered.dateOfDeath() == null
                            ? null : Date.valueOf(registered.dateOfDeath()))
                    .addValue("healthRegime", registered.healthRegime())
                    .addValue("payerUuid", registered.payerUuid() == null
                            ? null : UUID.fromString(registered.payerUuid()));
            case PatientReference.Unidentified unidentified -> parameters
                    .addValue("kind", "UNIDENTIFIED")
                    .addValue("code", unidentified.code())
                    .addValue("estimatedBirthYear", unidentified.estimatedBirthYear())
                    .addValue("status", unidentified.status().name())
                    .addValue("dateOfDeath", unidentified.dateOfDeath() == null
                            ? null : Date.valueOf(unidentified.dateOfDeath()))
                    .addValue("identifiedPatientUuid", unidentified.identifiedPatientUuid());
        }
        return parameters;
    }

    private static final RowMapper<PatientReference> ROW_MAPPER = (ResultSet row, int number) -> {
        String kind = row.getString("kind");
        UUID uuid = row.getObject("uuid", UUID.class);
        long version = row.getLong("source_version");
        PatientReference.Sex sex = PatientReference.Sex.valueOf(row.getString("sex"));
        if ("REGISTERED".equals(kind)) {
            return new PatientReference.Registered(uuid, version,
                    new PatientReference.Document(row.getString("document_type"), row.getString("document_number")),
                    row.getString("first_names"), row.getString("last_names"),
                    localDate(row, "birth_date"), sex,
                    PatientReference.Registered.Status.valueOf(row.getString("status")),
                    localDate(row, "date_of_death"), row.getString("health_regime"),
                    stringOrNull(row.getObject("payer_uuid", UUID.class)));
        }
        return new PatientReference.Unidentified(uuid, version, row.getString("code"), sex,
                row.getInt("estimated_birth_year"),
                PatientReference.Unidentified.Status.valueOf(row.getString("status")),
                row.getObject("identified_patient_uuid", UUID.class), localDate(row, "date_of_death"));
    };

    private static LocalDate localDate(ResultSet row, String column) throws SQLException {
        Date value = row.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
