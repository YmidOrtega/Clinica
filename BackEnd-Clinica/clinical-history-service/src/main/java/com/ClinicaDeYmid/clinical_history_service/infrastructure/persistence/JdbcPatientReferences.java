package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
class JdbcPatientReferences implements PatientReferences {

    private static final String COLUMNS = """
            uuid, kind, source_version, document_type, document_number, first_names, last_names, birth_date, code,
            estimated_birth_year, sex, status, date_of_death, health_regime, health_provider_nit, identified_patient_uuid""";

    private static final String INSERT = "INSERT INTO patient_references (" + COLUMNS + ", updated_at) VALUES "
            + "(:uuid, :kind, :sourceVersion, :documentType, :documentNumber, :firstNames, :lastNames, :birthDate, :code, "
            + ":estimatedBirthYear, :sex, :status, :dateOfDeath, :healthRegime, :healthProviderNit, :identifiedPatientUuid, :updatedAt)";

    private static final String UPDATE = """
            UPDATE patient_references SET kind = :kind, source_version = :sourceVersion, document_type = :documentType,
                document_number = :documentNumber, first_names = :firstNames, last_names = :lastNames, birth_date = :birthDate,
                code = :code, estimated_birth_year = :estimatedBirthYear, sex = :sex, status = :status,
                date_of_death = :dateOfDeath, health_regime = :healthRegime, health_provider_nit = :healthProviderNit,
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
        return jdbc.query("SELECT " + COLUMNS + " FROM patient_references WHERE uuid = :uuid",
                new MapSqlParameterSource("uuid", uuid.toString()), ROW_MAPPER).stream().findFirst();
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

    @Override
    public List<UUID> subjectsOf(UUID patientUuid) {
        List<UUID> provisional = jdbc.queryForList(
                "SELECT uuid FROM patient_references WHERE identified_patient_uuid = :uuid ORDER BY uuid",
                new MapSqlParameterSource("uuid", patientUuid.toString()), String.class).stream().map(UUID::fromString).toList();
        return Stream.concat(Stream.of(patientUuid), provisional.stream()).toList();
    }

    private MapSqlParameterSource parametersOf(PatientReference reference) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("uuid", reference.uuid().toString())
                .addValue("sourceVersion", reference.version())
                .addValue("sex", reference.sex().name())
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));
        switch (reference) {
            case PatientReference.Registered registered -> parameters
                    .addValue("kind", "REGISTERED")
                    .addValue("documentType", registered.document().type())
                    .addValue("documentNumber", registered.document().number())
                    .addValue("firstNames", registered.firstNames())
                    .addValue("lastNames", registered.lastNames())
                    .addValue("birthDate", Date.valueOf(registered.birthDate()))
                    .addValue("code", null)
                    .addValue("estimatedBirthYear", null)
                    .addValue("status", registered.status().name())
                    .addValue("dateOfDeath", toDate(registered.dateOfDeath()))
                    .addValue("healthRegime", registered.healthRegime())
                    .addValue("healthProviderNit", registered.healthProviderNit())
                    .addValue("identifiedPatientUuid", null);
            case PatientReference.Unidentified unidentified -> parameters
                    .addValue("kind", "UNIDENTIFIED")
                    .addValue("documentType", null)
                    .addValue("documentNumber", null)
                    .addValue("firstNames", null)
                    .addValue("lastNames", null)
                    .addValue("birthDate", null)
                    .addValue("code", unidentified.code())
                    .addValue("estimatedBirthYear", unidentified.estimatedBirthYear())
                    .addValue("status", unidentified.status().name())
                    .addValue("dateOfDeath", toDate(unidentified.dateOfDeath()))
                    .addValue("healthRegime", null)
                    .addValue("healthProviderNit", null)
                    .addValue("identifiedPatientUuid", unidentified.identifiedAs().map(UUID::toString).orElse(null));
        }
        return parameters;
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static final RowMapper<PatientReference> ROW_MAPPER = JdbcPatientReferences::toReference;

    private static PatientReference toReference(ResultSet row, int index) throws SQLException {
        UUID uuid = UUID.fromString(row.getString("uuid"));
        long version = row.getLong("source_version");
        PatientReference.Sex sex = PatientReference.Sex.valueOf(row.getString("sex"));
        LocalDate dateOfDeath = row.getObject("date_of_death", LocalDate.class);
        if ("REGISTERED".equals(row.getString("kind"))) {
            return new PatientReference.Registered(uuid, version,
                    new PatientReference.Document(row.getString("document_type"), row.getString("document_number")),
                    row.getString("first_names"), row.getString("last_names"), row.getObject("birth_date", LocalDate.class),
                    sex, PatientReference.Registered.Status.valueOf(row.getString("status")), dateOfDeath,
                    row.getString("health_regime"), row.getString("health_provider_nit"));
        }
        String identified = row.getString("identified_patient_uuid");
        return new PatientReference.Unidentified(uuid, version, row.getString("code"), sex, row.getInt("estimated_birth_year"),
                PatientReference.Unidentified.Status.valueOf(row.getString("status")),
                identified == null ? null : UUID.fromString(identified), dateOfDeath);
    }
}
