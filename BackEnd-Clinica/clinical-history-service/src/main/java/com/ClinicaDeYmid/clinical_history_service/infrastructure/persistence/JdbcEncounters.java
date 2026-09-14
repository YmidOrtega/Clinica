package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcEncounters implements Encounters {

    static final String SELECT = """
            SELECT e.id, e.patient_uuid, e.type, e.admission_id, e.opened_at, e.opened_by, e.opened_by_role,
                   c.closed_at, c.closed_by, c.closed_by_role
            FROM clinical_ledger.encounters e
            LEFT JOIN clinical_ledger.encounter_closures c ON c.encounter_id = e.id""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcEncounters(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Encounter encounter) {
        jdbc.update("""
                INSERT INTO clinical_ledger.encounters (id, patient_uuid, type, admission_id, opened_at, opened_by, opened_by_role)
                VALUES (:id, :patientUuid, :type, :admissionId, :openedAt, :openedBy, :openedByRole)""",
                new MapSqlParameterSource()
                        .addValue("id", encounter.id().toString())
                        .addValue("patientUuid", encounter.patientUuid().toString())
                        .addValue("type", encounter.type().name())
                        .addValue("admissionId", encounter.admissionId())
                        .addValue("openedAt", Rows.timestamp(encounter.openedAt()))
                        .addValue("openedBy", encounter.openedBy().uuid().toString())
                        .addValue("openedByRole", encounter.openedBy().role().name()));
    }

    @Override
    public Optional<Encounter> find(UUID id) {
        return jdbc.query(SELECT + " WHERE e.id = :id", new MapSqlParameterSource("id", id.toString()), JdbcEncounters::toEncounter)
                .stream().findFirst();
    }

    @Override
    public Optional<Encounter> lock(UUID id) {
        List<String> locked = jdbc.queryForList("SELECT id FROM clinical_ledger.encounters WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id.toString()), String.class);
        return locked.isEmpty() ? Optional.empty() : find(id);
    }

    @Override
    public void close(EncounterClosure closure) {
        jdbc.update("""
                INSERT INTO clinical_ledger.encounter_closures (encounter_id, closed_at, closed_by, closed_by_role)
                VALUES (:encounterId, :closedAt, :closedBy, :closedByRole)""",
                new MapSqlParameterSource()
                        .addValue("encounterId", closure.encounterId().toString())
                        .addValue("closedAt", Rows.timestamp(closure.closedAt()))
                        .addValue("closedBy", closure.closedBy().uuid().toString())
                        .addValue("closedByRole", closure.closedBy().role().name()));
    }

    @Override
    public List<Encounter> ofSubjects(Collection<UUID> patientUuids, int page, int size) {
        if (patientUuids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT + " WHERE e.patient_uuid IN (:patients) ORDER BY e.opened_at DESC, e.id LIMIT :size OFFSET :offset",
                new MapSqlParameterSource()
                        .addValue("patients", patientUuids.stream().map(UUID::toString).toList())
                        .addValue("size", size)
                        .addValue("offset", (long) page * size),
                JdbcEncounters::toEncounter);
    }

    static Encounter toEncounter(ResultSet row, int index) throws SQLException {
        Instant closedAt = Rows.instant(row, "closed_at");
        EncounterStatus status = closedAt == null
                ? new EncounterStatus.Open()
                : new EncounterStatus.Closed(closedAt, Rows.clinician(row, "closed_by", "closed_by_role"));
        return new Encounter(Rows.uuid(row, "id"), Rows.uuid(row, "patient_uuid"), EncounterType.valueOf(row.getString("type")),
                row.getString("admission_id"), Rows.instant(row, "opened_at"), Rows.clinician(row, "opened_by", "opened_by_role"),
                status);
    }
}
