package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeamMembership;
import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeams;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcCareTeams implements CareTeams {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCareTeams(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(UUID encounterId, Clinician member, Clinician addedBy, Instant addedAt) {
        try {
            return jdbc.update("""
                    INSERT INTO clinical_ledger.care_team_members (encounter_id, clinician_uuid, clinician_role, added_by, added_at)
                    VALUES (:encounterId, :clinicianUuid, :clinicianRole, :addedBy, :addedAt)""",
                    new MapSqlParameterSource()
                            .addValue("encounterId", encounterId.toString())
                            .addValue("clinicianUuid", member.uuid().toString())
                            .addValue("clinicianRole", member.role().name())
                            .addValue("addedBy", addedBy.uuid().toString())
                            .addValue("addedAt", Rows.timestamp(addedAt))) == 1;
        } catch (DuplicateKeyException alreadyMember) {
            return false;
        }
    }

    @Override
    public List<CareTeamMembership> membershipsOf(UUID clinicianUuid, Collection<UUID> patientUuids) {
        if (patientUuids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT m.encounter_id, e.patient_uuid, m.clinician_uuid, m.added_at, c.closed_at
                FROM clinical_ledger.care_team_members m
                JOIN clinical_ledger.encounters e ON e.id = m.encounter_id
                LEFT JOIN clinical_ledger.encounter_closures c ON c.encounter_id = m.encounter_id
                WHERE m.clinician_uuid = :clinicianUuid AND e.patient_uuid IN (:patients)""",
                new MapSqlParameterSource()
                        .addValue("clinicianUuid", clinicianUuid.toString())
                        .addValue("patients", patientUuids.stream().map(UUID::toString).toList()),
                (row, index) -> new CareTeamMembership(Rows.uuid(row, "encounter_id"), Rows.uuid(row, "patient_uuid"),
                        Rows.uuid(row, "clinician_uuid"), Rows.instant(row, "added_at"), Rows.instant(row, "closed_at")));
    }
}
